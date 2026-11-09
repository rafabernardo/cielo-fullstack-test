FROM node:22.19.0-alpine AS build
WORKDIR /app
COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile
COPY . .
# VITE_ variables are baked into the bundle at build time (.env is not copied into the image).
ARG VITE_API_URL=/api
RUN yarn build

FROM nginx:1.27-alpine
# The image fills ${API_PROXY_TARGET} from the environment at startup.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
