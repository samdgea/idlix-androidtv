module.exports = {
  apps: [
    {
      name: 'idlix-backend-api',
      script: './dist/server.js',
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '800M',
      env: {
        NODE_ENV: 'production',
        PORT: 5376,
        HOST: '0.0.0.0',
        IDLIX_BASE_URL: 'https://z3.idlixku.com',
        LOG_LEVEL: 'info'
      },
    },
  ],
};
