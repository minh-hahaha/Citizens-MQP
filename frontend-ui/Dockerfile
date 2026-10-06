#Download a preconfigured image with node js version 20 using alpine (a Linux distribution with a tiny footprint)
#A docker image is a a standardized package that includes all of the files, binaries, libraries, and configurations to run a container
#Name this preconfigured image as build
FROM node:20-alpine AS build
#Create a new directory in our image called app
WORKDIR /app
#Copy package.json and package-lock.json into the app directory
COPY package*.json ./
RUN npm install
#After running npm install, copy everything from this react app directory into the app directory
#By copying only package files first, it creates a Docker layer which means if the package files do not get changed Docker does not need to rerun npm install
COPY . .
RUN npm run build
#Nginx is the webserver that servers the static HTML and JS files created by building my react app
#Create a new docker image with this nginx image (overall dockerfile only outputs 1 image)
#Only the last FROM block in a dockerfile becomes the final image, but the previous images are stored in a temporary cache in case we want to do a COPY statement
FROM nginx:alpine AS production
#Copy static HTML and JS files from dist build folder and put them in public HTML Nginx folder
COPY --from=build /app/dist /usr/share/nginx/html
#Watch port 80 (standard for web development) also this is the default for nginx so this line isn't really necessary and is just for documentation purposes
EXPOSE 80
#Runs the docker container, if we didn't have this line the container would immediatley shut down since it has nothing running
#1 command with 3 arguments
#nginx starts web server, -g flag means we are going to pass a global configuration setting, and daemon off ensures nginx runs in the foreground
#If nginx runs in the background Docker container will stop running 
CMD ["nginx", "-g", "daemon off;"]