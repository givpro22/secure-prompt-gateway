# 프론트 이미지 — Vite로 정적 파일을 만들고 nginx가 그것만 서빙한다 (Node는 실행 이미지에 없음)
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# nginx가 같은 오리진에서 /api/v1을 백엔드로 프록시한다.
# 절대 URL을 빌드에 굽지 않아야 IP·도메인이 바뀔 때 이미지를 다시 만들 필요가 없다.
ARG VITE_API_BASE=/api/v1
ENV VITE_API_BASE=$VITE_API_BASE

# 빌드 컨텍스트에 .git이 없어서 vite가 커밋을 직접 읽을 수 없다. CI가 넘겨준다.
# 없으면 버전 줄이 화면에서 사라진다 — 로컬에서만 보이고 배포하면 안 보였던 이유다.
ARG GIT_COMMIT_COUNT=0
ARG GIT_SHA=""
ENV GIT_COMMIT_COUNT=$GIT_COMMIT_COUNT
ENV GIT_SHA=$GIT_SHA

RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
