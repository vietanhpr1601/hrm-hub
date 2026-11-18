# ----------- Build stage -----------
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files to leverage Docker cache
COPY package*.json ./

RUN npm install

# Copy all source files
COPY . .

# API cùng địa chỉ với trang web; Nginx chuyển /api sang backend.
# Biến môi trường thắng giá trị trong file .env khi Vite build.
ARG VITE_API_ENDPOINT=/api
ENV VITE_API_ENDPOINT=${VITE_API_ENDPOINT}

# Build the Vite application (generates the dist/ directory)
RUN npm run build


# ----------- Production stage -----------
FROM nginx:alpine

# Remove default nginx static files
RUN rm -rf /usr/share/nginx/html/*

# Copy high-performance build from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx: phục vụ SPA và chuyển /api sang container backend
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
