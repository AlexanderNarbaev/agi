FROM itzg/minecraft-server:java21

ENV TYPE=PAPER
ENV VERSION=1.20.4
ENV EULA=TRUE
ENV ONLINE_MODE=FALSE
ENV ENABLE_COMMAND_BLOCK=TRUE
ENV SPAWN_PROTECTION=0
ENV MAX_PLAYERS=20
ENV VIEW_DISTANCE=10
ENV MEMORY=1G

# Create plugins directory
RUN mkdir -p /data/plugins

# Copy Spigot plugin
COPY matrix-spigot/build/libs/matrix-spigot-*.jar /data/plugins/

EXPOSE 25565
