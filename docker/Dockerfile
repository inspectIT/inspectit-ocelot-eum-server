FROM openjdk:11-jre-slim
COPY ./docker/inspectit-ocelot-eum-server.jar /
COPY ./docker/entrypoint.sh  /
ENTRYPOINT ["sh", "entrypoint.sh"]