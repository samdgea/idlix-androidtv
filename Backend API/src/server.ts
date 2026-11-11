import 'dotenv/config';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import { apiRoutes } from './routes/api.js';
import { IdlixScraperEngine } from './engine/IdlixScraperEngine.js';

const fastify = Fastify({
  logger: {
    level: process.env.LOG_LEVEL || 'info',
  },
});

async function main() {
  await fastify.register(cors, {
    origin: true,
  });

  await fastify.register(apiRoutes);

  const port = parseInt(process.env.PORT || '5376', 10);
  const host = process.env.HOST || '0.0.0.0';

  try {
    await fastify.listen({ port, host });
    console.log(`[Server] Idlix Backend API listening on http://${host}:${port}`);

    // Pre-warm scraper browser session in the background
    IdlixScraperEngine.getInstance()
      .init()
      .then(() => IdlixScraperEngine.getInstance().login())
      .catch((err) => console.warn(`[Server] Initial scraper pre-warm notice: ${err?.message}`));
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
}

// Graceful shutdown
const shutdown = async () => {
  console.log('\n[Server] Shutting down gracefully...');
  await IdlixScraperEngine.getInstance().close();
  await fastify.close();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

main();
