FROM nginx:alpine

# Copy custom configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static files
COPY . /usr/share/nginx/html

# The base image already defines EXPOSE 80 and the ENTRYPOINT
