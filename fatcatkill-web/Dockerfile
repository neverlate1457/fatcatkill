FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_GATEWAY_URL
ARG VITE_ENABLE_DEBUG_MODE
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL
ENV VITE_ENABLE_DEBUG_MODE=$VITE_ENABLE_DEBUG_MODE
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
