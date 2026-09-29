# Headless-mode image, published as ghcr.io/cereal-automation/cereal (see the build-docker jobs in
# .github/workflows/release-client.yml).
#
# The build context is the jpackage app-image directory that `createReleaseDistributable` produces,
# the same one the Linux AppImage wraps, so the image never sees the repository (sekret.properties,
# Gradle properties) and no build secret can end up in a layer:
#
#   docker build -f cereal-client/docker/Dockerfile cereal-client/build/compose/binaries/main-release/app
#
# Chrome runs with its sandbox, which needs user/PID/network namespaces. Docker's default seccomp
# profile refuses those without CAP_SYS_ADMIN, so the container is started with seccomp.json from
# this directory (Docker's default plus exactly that):
#
#   docker run -it --security-opt seccomp=seccomp.json --shm-size=1g ghcr.io/cereal-automation/cereal
FROM debian:trixie-slim

LABEL org.opencontainers.image.source="https://github.com/Cereal-Automation/desktop-client"

# Chrome from Google's apt repository. No X server: the browser only runs --headless=new.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gpg tini; \
    curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg; \
    echo "deb [signed-by=/usr/share/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends google-chrome-stable; \
    # The package installs its own google-chrome.sources with the same key.
    rm /etc/apt/sources.list.d/google-chrome.list; \
    apt-get purge -y --auto-remove curl gpg; \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 1000 cereal \
    && useradd --uid 1000 --gid 1000 --create-home --home-dir /home/cereal cereal \
    && install -d -o cereal -g cereal /home/cereal/Cereal

# Root-owned, so the app user can't modify it.
COPY Cereal /opt/cereal

ENV CEREAL_DISTRIBUTION=docker \
    LANG=C.UTF-8

USER cereal
WORKDIR /home/cereal
VOLUME /home/cereal/Cereal
# Prompt page (#43).
EXPOSE 6080

# -g: signals go to the whole process group, so Chrome ends with the JVM on `docker stop`.
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/opt/cereal/bin/Cereal", "--headless"]
