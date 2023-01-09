FROM openjdk:11-jre-slim

# The docker directory contains the Dockerfile, entrypoint.sh and the eum-server jar to build the docker image.
# So the docker directory gets added to the container to successfully build the docker image.
# The dockerfile gets build in the docker directory where it is in and no new directories are being created.
# =====
# In order to build a docker image locally, the eum-server should be build locally and the resulting jar should be renamed
# to 'inspectit-ocelot-eum-server.jar' and copied to the ./docker directory
ADD ./docker/ / 
ENTRYPOINT ["sh", "/entrypoint.sh"]

