# syntax=docker/dockerfile:1

# ---- Build the static bundle ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Baked into the bundle at build time (Vite exposes only VITE_* variables).
ARG VITE_API_URL=http://localhost:8081
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---- Serve with nginx ----
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
