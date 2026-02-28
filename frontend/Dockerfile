# ============================================================
# Stage 1: Build
# ============================================================
FROM node:18-alpine AS build

WORKDIR /app

# -- Copy dependency manifests for layer caching --
COPY package.json package-lock.json ./

# -- Install dependencies (ci = clean install, deterministic) --
RUN npm ci --prefer-offline

# -- Copy source code --
COPY . .

# -- Build production bundle --
RUN npx ng build --configuration=production

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM nginx:1.25-alpine AS runtime

# -- Remove default nginx config --
RUN rm /etc/nginx/conf.d/default.conf

# -- Copy custom nginx config with SPA routing --
COPY nginx.conf /etc/nginx/conf.d/default.conf

# -- Copy built Angular app from build stage --
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# -- Security: run as non-root nginx user --
# nginx Alpine image already has user 'nginx' (uid 101)
# Adjust permissions for nginx to run as non-root
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
