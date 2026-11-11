import { connect } from 'puppeteer-real-browser';

export interface ResolvedPlaybackResult {
  title: string;
  hlsMasterUrl: string;
  subtitles: Array<{ lang: string; label: string; path: string }>;
  durationSec: number;
  videoId: string;
}

export class IdlixScraperEngine {
  private static instance: IdlixScraperEngine | null = null;
  private browser: any = null;
  private page: any = null;
  private isReady = false;
  private initPromise: Promise<void> | null = null;
  private baseUrl = process.env.IDLIX_BASE_URL || 'https://z2.idlixku.com';

  private constructor() {}

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

        // Block heavy external ads only, never block challenge resources
        try {
          await this.page.setRequestInterception(true);
          this.page.on('request', (req: any) => {
            const url = req.url();
            const adBlock = ['xyzchat', 'doubleclick', 'google-analytics', 'analytics.google.com'];

            if (adBlock.some((d) => url.includes(d))) {
              req.abort();
            } else {
              req.continue();
            }
          });
        } catch (_) {}

        console.log(`[ScraperEngine] Navigating to ${this.baseUrl} to solve Cloudflare challenge...`);
        await this.page.goto(this.baseUrl, {
          waitUntil: 'domcontentloaded',
          timeout: 60000,
        });

        console.log('[ScraperEngine] Waiting for Cloudflare Turnstile verification...');
        await this.page.waitForFunction(
          () => {
            const title = document.title.toLowerCase();
            return !title.includes('just a moment') && !title.includes('attention required');
          },
          { timeout: 45000 }
        );

        console.log(`[ScraperEngine] Cloudflare challenge bypassed successfully! Page Title: "${await this.page.title()}"`);
        this.isReady = true;
      } catch (err: any) {
        console.error(`[ScraperEngine] Failed to initialize browser session: ${err?.message || err}`);
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

  public getSessionStatus(): { isReady: boolean; targetUrl: string } {
    return {
      isReady: this.isReady && this.page !== null && !this.page.isClosed(),
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
    const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

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
            let parsedData = null;
            try {
              parsedData = JSON.parse(text);
            } catch (_) {
              parsedData = text;
            }

            return {
              status: res.status,
              data: parsedData,
              error: null,
            };
          } catch (e: any) {
            return {
              status: 500,
              data: null,
              error: e?.message || 'In-browser fetch error',
            };
          }
        },
        url,
        method,
        body,
        headers
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
      // If session might be stale or blocked, trigger re-init in background
      if (err?.message?.includes('Target closed') || err?.message?.includes('Session closed')) {
        this.isReady = false;
        this.page = null;
      }
      throw err;
    }
  }

  /**
   * Resolves complete HLS playback in a single call for high performance
   */
  public async resolvePlayback(
    contentType: string,
    contentId: string,
    contentTitle?: string
  ): Promise<ResolvedPlaybackResult> {
    await this.init();

    let targetType = contentType;
    let targetId = contentId;

    // Handle series: if tv_series is requested, resolve first episode of season 1
    if (contentType === 'tv_series') {
      const seriesRes = await this.executeFetch(`/api/series/${contentId}`);
      const series = seriesRes.data;
      const firstEp =
        series?.defaultSeason?.episodes?.[0] ||
        (series?.seasons?.[0]
          ? (await this.executeFetch(`/api/series/${series.slug}/season/${series.seasons[0].seasonNumber}/episode/1`)).data?.episode
          : null);

      if (firstEp?.id) {
        targetType = 'episode';
        targetId = firstEp.id;
      } else {
        throw new Error('No episodes available for this series');
      }
    }

    // Step 1: Get play-info (gateToken + countdown)
    const playInfoRes = await this.executeFetch(`/api/watch/play-info/${targetType}/${targetId}`);
    const playInfo = playInfoRes.data;
    if (!playInfo?.gateToken) {
      throw new Error('Failed to obtain playback gate token from provider');
    }

    // Extract device ID (did) from gateToken payload
    let didCookie: string | undefined;
    try {
      const rawP0 = playInfo.gateToken.split('.')[0];
      const decodedP0 = Buffer.from(rawP0, 'base64').toString('utf-8');
      const didMatch = /"owner":"d:([^"]+)"/.exec(decodedP0);
      if (didMatch?.[1]) {
        didCookie = `did=${didMatch[1]}`;
      }
    } catch (_) {}

    // Step 2: Handle unlock countdown
    const serverNow = playInfo.serverNow || Date.now();
    const unlockAt = playInfo.unlockAt || serverNow + 15000;
    const waitMs = Math.max(0, unlockAt - serverNow);

    if (waitMs > 0) {
      console.log(`[ScraperEngine] Synchronizing with provider countdown (${Math.round(waitMs / 1000)}s)...`);
      await new Promise((resolve) => setTimeout(resolve, Math.min(waitMs + 500, 16000)));
    }

    // Step 3: Claim session
    let claimRes: any = null;
    for (let attempt = 1; attempt <= 10; attempt++) {
      try {
        const claimResp = await this.executeFetch('/api/watch/session/claim', {
          method: 'POST',
          body: { gateToken: playInfo.gateToken },
          headers: didCookie ? { Cookie: didCookie } : undefined,
        });

        if (claimResp.data?.kind === 'pentos' && claimResp.data?.claim) {
          claimRes = claimResp.data;
          break;
        }
      } catch (_) {}
      await new Promise((r) => setTimeout(r, 600));
    }

    if (!claimRes?.claim) {
      throw new Error('Failed to claim playback session from provider');
    }

    // Step 4: Redeem HLS master playlist from Majorplay CDN / Idlix session redeem
    const rawRedeemUrl = claimRes.redeemUrl || '/api/watch/session/redeem';
    const redeemUrl = rawRedeemUrl.startsWith('http')
      ? rawRedeemUrl
      : rawRedeemUrl.startsWith('/api/')
      ? rawRedeemUrl
      : `/api${rawRedeemUrl.startsWith('/') ? '' : '/'}${rawRedeemUrl}`;
    const redeemResp = await this.executeFetch(redeemUrl, {
      method: 'POST',
      body: { claim: claimRes.claim, mode: 'browser' },
      headers: {
        ...(didCookie ? { Cookie: didCookie } : {}),
        Referer: `${this.baseUrl}/`,
        Origin: this.baseUrl,
      },
    });

    const redeemData = redeemResp.data;
    if (!redeemData?.url) {
      throw new Error('Failed to redeem HLS master playlist URL');
    }

    return {
      title: claimRes.title || contentTitle || 'Video',
      hlsMasterUrl: redeemData.url,
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
    }
  }
}
