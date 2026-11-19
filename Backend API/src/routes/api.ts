import { FastifyInstance, FastifyPluginAsync } from 'fastify';
import { IdlixScraperEngine } from '../engine/IdlixScraperEngine.js';
import { globalCache } from '../services/CacheService.js';

export const apiRoutes: FastifyPluginAsync = async (fastify: FastifyInstance) => {
  const engine = IdlixScraperEngine.getInstance();

  const forwardAuthHeaders = (headers: Record<string, any>): Record<string, string> => {
    const out: Record<string, string> = {};
    const forwardKeys = ['x-client-version', 'x-client-fp', 'x-refresh-recovery'];
    for (const key of forwardKeys) {
      const val = headers[key];
      if (typeof val === 'string' && val.trim()) {
        out[key] = val;
      }
    }
    return out;
  };

  const hasClientAuthCookie = (cookieHeader?: string): boolean => {
    if (!cookieHeader) return false;
    return cookieHeader
      .split(';')
      .map((part) => part.trim().split('=')[0]?.toLowerCase())
      .some((name) => Boolean(name && name !== 'did' && name !== 'cf_clearance' && name !== '__cf_bm'));
  };

  const sendWithSetCookies = (
    reply: any,
    status: number,
    data: any,
    setCookies: string[]
  ) => {
    if (setCookies.length > 0) {
      reply.header('set-cookie', setCookies);
    }
    return reply.status(status).send(data);
  };

  // Health and Status
  fastify.get('/health', async () => {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
      session: engine.getSessionStatus(),
    };
  });

  // Movies list
  fastify.get<{
    Querystring: { page?: string; limit?: string; sort?: string; genre?: string };
  }>('/api/movies', async (req, reply) => {
    const { page = '1', limit = '20', sort, genre } = req.query;
    const cacheKey = `movies:${page}:${limit}:${sort || ''}:${genre || ''}`;

    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const queryParams = new URLSearchParams({ page, limit });
    if (sort) queryParams.set('sort', sort);
    if (genre) queryParams.set('genre', genre);

    const path = `/api/movies?${queryParams.toString()}`;
    const result = await engine.executeFetch(path);

    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 15 * 60 * 1000); // 15 min cache
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Series list
  fastify.get<{
    Querystring: { page?: string; limit?: string; sort?: string; genre?: string };
  }>('/api/series', async (req, reply) => {
    const { page = '1', limit = '20', sort, genre } = req.query;
    const cacheKey = `series:${page}:${limit}:${sort || ''}:${genre || ''}`;

    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const queryParams = new URLSearchParams({ page, limit });
    if (sort) queryParams.set('sort', sort);
    if (genre) queryParams.set('genre', genre);

    const path = `/api/series?${queryParams.toString()}`;
    const result = await engine.executeFetch(path);

    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 15 * 60 * 1000);
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Movie detail
  fastify.get<{ Params: { slug: string } }>('/api/movies/:slug', async (req, reply) => {
    const { slug } = req.params;
    const cacheKey = `movie_detail:${slug}`;

    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const result = await engine.executeFetch(`/api/movies/${slug}`);
    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 30 * 60 * 1000); // 30 min cache
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Series detail
  fastify.get<{ Params: { slug: string } }>('/api/series/:slug', async (req, reply) => {
    const { slug } = req.params;
    const cacheKey = `series_detail:${slug}`;

    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const result = await engine.executeFetch(`/api/series/${slug}`);
    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 30 * 60 * 1000);
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Episode detail
  fastify.get<{
    Params: { slug: string; season: string; episode: string };
  }>('/api/series/:slug/season/:season/episode/:episode', async (req, reply) => {
    const { slug, season, episode } = req.params;
    const cacheKey = `episode:${slug}:${season}:${episode}`;

    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const result = await engine.executeFetch(`/api/series/${slug}/season/${season}/episode/${episode}`);
    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 30 * 60 * 1000);
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Genres
  fastify.get('/api/genres', async (_req, reply) => {
    const cacheKey = 'genres:all';
    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const result = await engine.executeFetch('/api/genres');
    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 2 * 60 * 60 * 1000); // 2 hours
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Search
  fastify.get<{ Querystring: { q?: string } }>('/api/search', async (req, reply) => {
    const query = req.query.q?.trim();
    if (!query) {
      return { results: [], total: 0 };
    }

    const cacheKey = `search:${query.toLowerCase()}`;
    const cached = globalCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const result = await engine.executeFetch(`/api/search?q=${encodeURIComponent(query)}`);
    if (result.status === 200 && result.data) {
      globalCache.set(cacheKey, result.data, 10 * 60 * 1000);
      return result.data;
    }

    return reply.status(result.status).send(result.data);
  });

  // Auth passthrough (stateless per-client cookies, isolated from global Puppeteer jar)
  fastify.post('/api/auth/login', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/login', {
      method: 'POST',
      body: req.body,
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.get('/api/auth/me', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/me', {
      method: 'GET',
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.post<{ Querystring: { bootstrap?: string } }>('/api/auth/refresh', async (req, reply) => {
    const bootstrap = req.query.bootstrap ?? '0';
    const result = await engine.executeIsolatedFetch(`/api/auth/refresh?bootstrap=${encodeURIComponent(bootstrap)}`, {
      method: 'POST',
      body: req.body ?? {},
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.post('/api/auth/logout', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/logout', {
      method: 'POST',
      body: req.body ?? { scope: 'local' },
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.get('/api/auth/sessions', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/sessions', {
      method: 'GET',
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.post('/api/auth/sessions/revoke', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/sessions/revoke', {
      method: 'POST',
      body: req.body,
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  fastify.post('/api/auth/sessions/logout-others', async (req, reply) => {
    const result = await engine.executeIsolatedFetch('/api/auth/sessions/logout-others', {
      method: 'POST',
      body: req.body ?? {},
      headers: forwardAuthHeaders(req.headers),
      clientCookie: req.headers.cookie as string | undefined,
    });
    return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
  });

  // Play info (gateToken & countdown)
  fastify.get<{ Params: { contentType: string; id: string } }>(
    '/api/watch/play-info/:contentType/:id',
    async (req, reply) => {
      const { contentType, id } = req.params;
      const incomingCookie = (req.headers.cookie as string) || undefined;
      if (hasClientAuthCookie(incomingCookie)) {
        const result = await engine.executeIsolatedFetch(`/api/watch/play-info/${contentType}/${id}`, {
          method: 'GET',
          clientCookie: incomingCookie,
        });
        return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
      }
      const result = await engine.executeFetch(`/api/watch/play-info/${contentType}/${id}`);
      return reply.status(result.status).send(result.data);
    }
  );

  // Claim session
  fastify.post<{ Body: { gateToken: string; verificationToken?: string } }>('/api/watch/session/claim', async (req, reply) => {
    const { gateToken, verificationToken } = req.body;
    if (!gateToken) {
      return reply.status(400).send({ error: 'gateToken is required' });
    }

    let didCookie: string | undefined;
    try {
      const rawP0 = gateToken.split('.')[0];
      const decodedP0 = Buffer.from(rawP0, 'base64').toString('utf-8');
      const didMatch = /"owner":"d:([^"]+)"/.exec(decodedP0);
      if (didMatch?.[1]) {
        didCookie = `did=${didMatch[1]}`;
      }
    } catch (_) {}

    const incomingCookie = (req.headers.cookie as string) || undefined;
    if (hasClientAuthCookie(incomingCookie)) {
      const result = await engine.executeIsolatedFetch('/api/watch/session/claim', {
        method: 'POST',
        body: { gateToken, ...(verificationToken ? { verificationToken } : {}) },
        clientCookie: incomingCookie,
      });
      return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
    }

    const cookieHeader = didCookie || incomingCookie;

    const result = await engine.executeFetch('/api/watch/session/claim', {
      method: 'POST',
      body: { gateToken, ...(verificationToken ? { verificationToken } : {}) },
      headers: cookieHeader ? { Cookie: cookieHeader } : undefined,
    });

    return reply.status(result.status).send(result.data);
  });

  // Redeem Play (Majorplay CDN / Idlix Session Redeem)
  fastify.post<{ Body: { url?: string; claim: string; mode?: string } }>(
    '/api/watch/redeem',
    async (req, reply) => {
      const { url = '/api/watch/session/redeem', claim, mode = 'browser' } = req.body;
      if (!claim) {
        return reply.status(400).send({ error: 'claim is required' });
      }

      const normalizedUrl = url.startsWith('http')
        ? url
        : url.startsWith('/api/')
        ? url
        : `/api${url.startsWith('/') ? '' : '/'}${url}`;
      const incomingCookie = (req.headers.cookie as string) || undefined;
      const targetOrigin = process.env.IDLIX_BASE_URL || 'https://z3.idlixku.com';

      if (hasClientAuthCookie(incomingCookie)) {
        const result = await engine.executeIsolatedFetch(normalizedUrl, {
          method: 'POST',
          body: { claim, mode },
          headers: {
            Referer: `${targetOrigin}/`,
            Origin: targetOrigin,
          },
          clientCookie: incomingCookie,
        });
        return sendWithSetCookies(reply, result.status, result.data, result.setCookies);
      }

      const result = await engine.executeFetch(normalizedUrl, {
        method: 'POST',
        body: { claim, mode },
        headers: {
          ...(incomingCookie ? { Cookie: incomingCookie } : {}),
          Referer: `${targetOrigin}/`,
          Origin: targetOrigin,
        },
      });

      return reply.status(result.status).send(result.data);
    }
  );

  // High-performance All-in-One Playback Resolver
  fastify.get<{
    Querystring: { contentType?: string; contentId?: string; contentTitle?: string };
  }>('/api/playback/resolve', async (req, reply) => {
    const { contentType, contentId, contentTitle } = req.query;
    if (!contentType || !contentId) {
      return reply.status(400).send({ error: 'contentType and contentId are required' });
    }

    try {
      const playback = await engine.resolvePlayback(contentType, contentId, contentTitle);
      return playback;
    } catch (err: any) {
      req.log.error(err, 'Failed to resolve stream playback');
      return reply.status(500).send({
        error: err?.message || 'Failed to resolve stream playback',
      });
    }
  });
};
