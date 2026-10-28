import { FastifyInstance, FastifyPluginAsync } from 'fastify';
import { IdlixScraperEngine } from '../engine/IdlixScraperEngine.js';
import { globalCache } from '../services/CacheService.js';

export const apiRoutes: FastifyPluginAsync = async (fastify: FastifyInstance) => {
  const engine = IdlixScraperEngine.getInstance();

  // Health and Status
  fastify.get('/health', async () => {
    const session = engine.getSessionStatus();
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
      session: {
        isReady: session.isReady,
        isLoggedIn: session.isLoggedIn, // Status login
        targetUrl: session.targetUrl,
      },
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

  // Play info (gateToken & countdown)
  fastify.get<{ Params: { contentType: string; id: string } }>(
    '/api/watch/play-info/:contentType/:id',
    async (req, reply) => {
      const { contentType, id } = req.params;
      const result = await engine.executeFetch(`/api/watch/play-info/${contentType}/${id}`);
      return reply.status(result.status).send(result.data);
    }
  );

  // Claim session
  fastify.post<{ Body: { gateToken: string } }>('/api/watch/session/claim', async (req, reply) => {
    const { gateToken } = req.body;
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
    } catch (_) { }

    const result = await engine.executeFetch('/api/watch/session/claim', {
      method: 'POST',
      body: { gateToken },
      headers: didCookie ? { Cookie: didCookie } : undefined,
    });

    return reply.status(result.status).send(result.data);
  });

  // Redeem Play (Majorplay CDN)
  fastify.post<{ Body: { url?: string; claim: string; mode?: string } }>(
    '/api/watch/redeem',
    async (req, reply) => {
      const { url = 'https://e2e.majorplay.net/api/play', claim, mode = 'browser' } = req.body;
      if (!claim) {
        return reply.status(400).send({ error: 'claim is required' });
      }

      const result = await engine.executeFetch(url, {
        method: 'POST',
        body: { claim, mode },
        headers: {
          Referer: 'https://z2.idlixku.com/',
          Origin: 'https://z2.idlixku.com',
        },
      });

      return reply.status(result.status).send(result.data);
    }
  );

  // High-performance All-in-One Playback Resolver
  fastify.get<{
    Querystring: {
      contentType?: string;
      contentId?: string;
      contentTitle?: string;
      quality?: string;              // ← BARU: "max" | "1080p" | "720p" | ...
    };
  }>('/api/playback/resolve', async (req, reply) => {
    const { contentType, contentId, contentTitle, quality } = req.query;
    if (!contentType || !contentId) {
      return reply.status(400).send({ error: 'contentType and contentId are required' });
    }
    try {
      const playback = await engine.resolvePlayback(contentType, contentId, contentTitle, quality);
      return playback;
    } catch (err: any) {
      req.log.error(err, 'Failed to resolve stream playback');
      return reply.status(500).send({ error: err?.message || 'Failed to resolve stream playback' });
    }
  });
};
