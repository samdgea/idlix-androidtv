import { connect } from 'puppeteer-real-browser';

export interface ResolvedPlaybackResult {
  title: string;
  hlsMasterUrl: string;
  hlsVariantUrl: string;
  selectedQuality: string;
  availableQualities: Array<{
    name: string;
    width?: number;
    height?: number;
    bandwidth: number;
    url: string;
  }>;
  subtitles: Array<{ lang: string; label: string; path: string }>;
  durationSec: number;
  videoId: string;
}

export class IdlixScraperEngine {
  private isLoggedIn = false;
  private static instance: IdlixScraperEngine | null = null;
  private browser: any = null;
  private page: any = null;
  private isReady = false;
  private baseUrl = process.env.IDLIX_BASE_URL || 'https://z2.idlixku.com';
  private initPromise: Promise<void> | null = null;
  private loginPromise: Promise<boolean> | null = null;   // ← hindari login paralel
  // ← fallback baseUrl (jangan undefined)

  private constructor() { }

  public static getInstance(): IdlixScraperEngine {
    if (!IdlixScraperEngine.instance) {
      IdlixScraperEngine.instance = new IdlixScraperEngine();
    }
    return IdlixScraperEngine.instance;
  }

  public async init(): Promise<void> {
    if (this.isReady && this.page && !this.page.isClosed()) {
      return;
    }
    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = (async () => {
      console.log(`[ScraperEngine] Launching real-browser stealth engine for Cloudflare bypass at ${this.baseUrl}...`);

      try {
        const browserArgs = [
          '--no-sandbox',
          '--disable-setuid-sandbox',
          '--disable-dev-shm-usage',
          '--disable-gpu',
          '--no-first-run',
          '--no-zygote',
          '--window-size=1920,1080',
          '--lang=id-ID,id,en-US,en',
        ];

        // Option A: puppeteer-real-browser with Xvfb virtual display on Linux VPS & SOCKS5 proxy
        const realBrowser = await (connect as any)({
          headless: false,
          turnstile: true,
          disableXvfb: process.platform !== 'linux',
          args: browserArgs,
        });

        this.browser = realBrowser.browser;
        this.page = realBrowser.page;

        await this.page.setUserAgent(
          'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 ' +
          '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
        );
        // Block media and ad networks to save VPS RAM and CPU
        await this.page.setRequestInterception(true);
        this.page.on('request', (req: any) => {
          const resType = req.resourceType();
          const url = req.url();
          const adBlock = ['xyzchat', 'doubleclick', 'google-analytics', 'analytics.google.com'];
          if (
            resType === 'image' ||
            resType === 'media' ||
            resType === 'font' ||
            adBlock.some((d) => url.includes(d))
          ) {
            req.abort();
          } else {
            req.continue();
          }
        });

        console.log(`[ScraperEngine] Navigating to ${this.baseUrl} to solve challenge...`);
        await this.page.goto(this.baseUrl, {
          waitUntil: 'domcontentloaded',
          timeout: 45000
        });
        // Wait until Cloudflare challenge passes (title does not contain "Just a moment...")
        await this.page.waitForFunction(
          () => {
            const title = document.title.toLowerCase();
            return !title.includes('just a moment') && !title.includes('attention required');
          },
          { timeout: 45000 }
        );

        console.log(`[ScraperEngine] Cloudflare challenge bypassed successfully! Page Title: "${await this.page.title()}"`);
        this.isReady = true;

        // Auto re-login setelah init (penting jika sebelumnya sudah pernah login)
        this.isLoggedIn = false;   // reset karena browser baru
        if (process.env.IDLIX_EMAIL && process.env.IDLIX_PASSWORD) {
          await this.login().catch((e) =>
            console.warn(`[ScraperEngine] Auto-login after init failed: ${e?.message}`)
          );
        }
      } catch (err: any) {
        console.error(`[ScraperEngine] Init failed: ${err?.message || err}`);
        this.isReady = false;
        if (this.browser) {
          try {
            await this.browser.close();
          } catch (_) {}
          this.browser = null;
          this.page = null;
        }
        throw err;
      } finally {
        this.initPromise = null;
      }
    })();

    return this.initPromise;
  }

  /**
   * Login otomatis. Dilindungi loginPromise agar tidak terjadi paralel.
   */
  public async login(): Promise<boolean> {
    if (this.isLoggedIn) return true;
    if (this.loginPromise) return this.loginPromise;

    this.loginPromise = (async () => {
      const email = process.env.IDLIX_EMAIL;
      const password = process.env.IDLIX_PASSWORD;

      if (!email || !password) {
        console.warn('[ScraperEngine] Kredensial login tidak tersedia.');
        return false;
      }

      await this.init();
      if (!this.page || this.page.isClosed()) return false;

      try {
        console.log('[ScraperEngine] Mencoba login otomatis...');

        // 1. Gunakan networkidle2 agar React selesai merender navbar/modal
        await this.page.goto(this.baseUrl, { waitUntil: 'networkidle2', timeout: 45000 });

        // 2. Cek apakah sudah login (misal ada text "Profil" atau "Logout")
        const alreadyLoggedIn = await this.page.evaluate(() => {
          return document.body.innerText.toLowerCase().includes('logout') ||
            document.body.innerText.toLowerCase().includes('profil');
        });
        if (alreadyLoggedIn) {
          console.log('[ScraperEngine] ✅ Sudah dalam keadaan login.');
          this.isLoggedIn = true;
          return true;
        }

        // 3. Cari dan klik tombol Masuk
        console.log('[ScraperEngine] Mencari tombol Masuk...');
        const clicked = await this.page.evaluate(() => {
          const elements = Array.from(document.querySelectorAll('a, button, [role="button"]'));
          const loginBtn = elements.find(el => {
            const text = (el.textContent || '').toLowerCase().trim();
            const href = el.getAttribute('href') || '';
            return text === 'masuk' || text === 'login' || href.includes('/login');
          });
          if (loginBtn) {
            (loginBtn as HTMLElement).click();
            return true;
          }
          return false;
        });

        if (!clicked) throw new Error('Tombol "Masuk" tidak ditemukan di Navbar.');

        // 4. Tunggu form modal muncul (menggunakan selektor dari screenshot Anda)
        console.log('[ScraperEngine] Menunggu form login muncul...');
        const emailSel = 'input[autocomplete="username"]';
        const passSel = 'input[type="password"]';

        await this.page.waitForSelector(emailSel, { visible: true, timeout: 15000 });

        // 5. Isi form (Bersihkan dulu dengan evaluate)
        await this.page.$eval(emailSel, (el: any) => { (el as HTMLInputElement).value = ''; });
        await this.page.type(emailSel, email, { delay: 50 });

        await this.page.$eval(passSel, (el: any) => { (el as HTMLInputElement).value = ''; });
        await this.page.type(passSel, password, { delay: 50 });

        // 6. Submit tanpa menunggu navigasi URL (karena ini SPA)
        console.log('[ScraperEngine] Menekan tombol Submit...');
        await this.page.click('button[type="submit"]');

        // 7. VERIFIKASI: Tunggu sampai form password hilang dari layar
        // (Jika modal tertutup, berarti login sukses)
        console.log('[ScraperEngine] Menunggu verifikasi login...');
        await this.page.waitForFunction(
          () => !document.querySelector('input[type="password"]'),
          { timeout: 15000 }
        ).catch(() => {
          throw new Error('Login gagal: Form password masih tampil (Cek email/password).');
        });

        this.isLoggedIn = true;
        console.log('[ScraperEngine] ✅ Login berhasil!.');
        return true;
      } catch (err: any) {
        console.error(`[ScraperEngine] ❌ Login gagal: ${err?.message}`);
        this.isLoggedIn = false;
        return false;
      } finally {
        this.loginPromise = null;
      }
    })();

    return this.loginPromise;
  }

  /**
   * Pastikan login aktif. Panggil sebelum operasi kritikal (redeem 1080p).
   */
  private async ensureLoggedIn(): Promise<void> {
    if (this.isLoggedIn) return;
    const ok = await this.login();
    if (!ok) {
      console.warn('[ScraperEngine] Lanjut tanpa login — resolusi dibatasi (mungkin max 720p).');
    }
  }

  // ← TIPE RETURN DIPERBAIKI
  public getSessionStatus(): { isReady: boolean; isLoggedIn: boolean; targetUrl: string } {
    return {
      isReady: this.isReady && this.page !== null && !this.page.isClosed(),
      isLoggedIn: this.isLoggedIn,
      targetUrl: this.baseUrl,
    };
  }

  public async executeFetch<T = any>(
    endpoint: string,
    options: {
      method?: 'GET' | 'POST';
      body?: any;
      headers?: Record<string, string>;
      timeoutMs?: number;
    } = {}
  ): Promise<{ status: number; data: T }> {
    await this.init();

    if (!this.page || this.page.isClosed()) {
      throw new Error('Browser page session is closed');
    }

    const { method = 'GET', body, headers = {} } = options;
    const url = endpoint.startsWith('http')
      ? endpoint
      : `${this.baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

    try {
      const result = await this.page.evaluate(
        async (
          fetchUrl: string,
          fetchMethod: string,
          fetchBody: any,
          fetchHeaders: Record<string, string>
        ) => {
          try {
            const res = await fetch(fetchUrl, {
              method: fetchMethod,
              headers: {
                Accept: 'application/json, text/plain, */*',
                'Content-Type': 'application/json',
                ...fetchHeaders,
              },
              body: fetchBody ? JSON.stringify(fetchBody) : undefined,
            });
            const text = await res.text();
            let parsed: any;
            try { parsed = JSON.parse(text); } catch { parsed = text; }
            return { status: res.status, data: parsed, error: null };
          } catch (e: any) {
            return { status: 500, data: null, error: e?.message || 'in-browser fetch error' };
          }
        },
        url, method, body, headers
      );

      if (result.error) {
        throw new Error(result.error);
      }

      // If Cloudflare returns 403 interstitial, mark session invalid so it re-solves next time
      if (
        result.status === 403 &&
        typeof result.data === 'string' &&
        (result.data.includes('Just a moment') ||
          result.data.includes('cf-mitigated') ||
          result.data.includes('challenges.cloudflare.com'))
      ) {
        console.warn(`[ScraperEngine] Received Cloudflare 403 challenge for ${url}. Invalidating scraper session.`);
        this.isReady = false;
        this.page = null;
      }

      return { status: result.status, data: result.data };
    } catch (err: any) {
      console.warn(`[ScraperEngine] Request failed for ${url}: ${err?.message}`);
      if (err?.message?.includes('Target closed') || err?.message?.includes('Session closed')) {
        this.isReady = false;
        this.page = null;
        this.isLoggedIn = false;   // ← session mati = login juga hilang
      }
      throw err;
    }
  }

  // ============ HLS variant parsing ============
  private parseMasterPlaylist(text: string, baseUrl: string): ResolvedPlaybackResult['availableQualities'] {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const variants: ResolvedPlaybackResult['availableQualities'] = [];

    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].startsWith('#EXT-X-STREAM-INF:')) continue;
      const attrs = lines[i].substring('#EXT-X-STREAM-INF:'.length);
      const bw = /BANDWIDTH=(\d+)/.exec(attrs);
      const res = /RESOLUTION=(\d+)x(\d+)/.exec(attrs);
      let uri = '';
      for (let j = i + 1; j < lines.length; j++) {
        if (!lines[j].startsWith('#')) { uri = lines[j]; break; }
      }
      if (!uri) continue;

      const fullUrl = uri.startsWith('http') ? uri : new URL(uri, baseUrl).toString();
      const height = res ? parseInt(res[2], 10) : 0;
      const bandwidth = bw ? parseInt(bw[1], 10) : 0;

      variants.push({
        name: height ? `${height}p` : `${Math.round(bandwidth / 1000)}kbps`,
        width: res ? parseInt(res[1], 10) : undefined,
        height: height || undefined,
        bandwidth,
        url: fullUrl,
      });
    }
    variants.sort((a, b) => (b.height ?? 0) - (a.height ?? 0) || b.bandwidth - a.bandwidth);
    return variants;
  }

  private selectVariant(
    variants: ResolvedPlaybackResult['availableQualities'],
    quality?: string
  ): ResolvedPlaybackResult['availableQualities'][number] | null {
    if (!variants.length) return null;
    const q = (quality || 'max').toLowerCase();
    if (['max', 'best', 'highest', 'auto'].includes(q)) return variants[0];

    const m = /(\d+)/.exec(q);
    if (!m) return variants[0];
    const target = parseInt(m[1], 10);
    return variants.find((v) => v.height === target)
      ?? variants.find((v) => (v.height ?? 0) >= target)
      ?? variants[0];
  }

  public async resolvePlayback(
    contentType: string,
    contentId: string,
    contentTitle?: string,
    quality?: string
  ): Promise<ResolvedPlaybackResult> {
    await this.init();
    await this.ensureLoggedIn();   // ← pastikan login untuk akses 1080p

    let targetType = contentType;
    let targetId = contentId;

    if (contentType === 'tv_series') {
      const seriesRes = await this.executeFetch(`/api/series/${contentId}`);
      const series: any = seriesRes.data;
      const firstEp =
        series?.defaultSeason?.episodes?.[0] ||
        (series?.seasons?.[0]
          ? (await this.executeFetch(
            `/api/series/${series.slug}/season/${series.seasons[0].seasonNumber}/episode/1`
          )).data?.episode
          : null);
      if (firstEp?.id) {
        targetType = 'episode';
        targetId = firstEp.id;
      } else {
        throw new Error('No episodes available for this series');
      }
    }

    const playInfoRes = await this.executeFetch(`/api/watch/play-info/${targetType}/${targetId}`);
    const playInfo: any = playInfoRes.data;
    if (!playInfo?.gateToken) throw new Error('Failed to obtain playback gate token');

    let didCookie: string | undefined;
    try {
      const rawP0 = playInfo.gateToken.split('.')[0];
      const decodedP0 = Buffer.from(rawP0, 'base64').toString('utf-8');
      const didMatch = /"owner":"d:([^"]+)"/.exec(decodedP0);
      if (didMatch?.[1]) didCookie = `did=${didMatch[1]}`;
    } catch { }

    const serverNow = playInfo.serverNow || Date.now();
    const unlockAt = playInfo.unlockAt || serverNow + 15000;
    const waitMs = Math.max(0, unlockAt - serverNow);
    if (waitMs > 0) {
      console.log(`[ScraperEngine] Waiting countdown (${Math.round(waitMs / 1000)}s)...`);
      await new Promise((r) => setTimeout(r, Math.min(waitMs + 500, 16000)));
    }

    let claimRes: any = null;
    for (let attempt = 1; attempt <= 10; attempt++) {
      try {
        const r = await this.executeFetch('/api/watch/session/claim', {
          method: 'POST',
          body: { gateToken: playInfo.gateToken },
          headers: didCookie ? { Cookie: didCookie } : undefined,
        });
        if ((r.data as any)?.kind === 'pentos' && (r.data as any)?.claim) {
          claimRes = r.data;
          break;
        }
      } catch { }
      await new Promise((r) => setTimeout(r, 600));
    }
    if (!claimRes?.claim) throw new Error('Failed to claim playback session');

    const redeemUrl = claimRes.redeemUrl || 'https://e2e.majorplay.net/api/play';
    const redeemResp = await this.executeFetch(redeemUrl, {
      method: 'POST',
      body: { claim: claimRes.claim, mode: 'browser' },
      headers: { Referer: `${this.baseUrl}/`, Origin: this.baseUrl },
    });
    const redeemData: any = redeemResp.data;
    if (!redeemData?.url) throw new Error('Failed to redeem HLS master playlist URL');

    // Parse master playlist → pilih variant sesuai permintaan quality
    let variants: ResolvedPlaybackResult['availableQualities'] = [];
    let selectedUrl = redeemData.url;
    let selectedName = 'auto';
    try {
      const masterResp = await this.executeFetch(redeemData.url);
      if (typeof masterResp.data === 'string' && (masterResp.data as string).includes('#EXTM3U')) {
        variants = this.parseMasterPlaylist(masterResp.data as string, redeemData.url);
        const chosen = this.selectVariant(variants, quality);
        if (chosen) { selectedUrl = chosen.url; selectedName = chosen.name; }
      }
    } catch (e: any) {
      console.warn(`[ScraperEngine] Master parse failed: ${e?.message}`);
    }

    return {
      title: claimRes.title || contentTitle || 'Video',
      hlsMasterUrl: redeemData.url,
      hlsVariantUrl: selectedUrl,
      selectedQuality: selectedName,
      availableQualities: variants,
      subtitles: Array.isArray(redeemData.subtitles) ? redeemData.subtitles : [],
      durationSec: claimRes.durationSec || 0,
      videoId: claimRes.videoId || '',
    };
  }

  public async close(): Promise<void> {
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
      this.page = null;
      this.isReady = false;
      this.isLoggedIn = false;   // ← reset
    }
  }
}
