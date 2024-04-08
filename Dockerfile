# syntax=docker.io/docker/dockerfile:1

# build stage: includes resources necessary for installing dependencies

# Here the image's platform does not necessarily have to be riscv64.
# If any needed dependencies rely on native binaries, you must use 
# a riscv64 image such as cartesi/node:20-jammy for the build stage,
# to ensure that the appropriate binaries will be generated.
FROM debian:trixie as java-stage

RUN <<EOF
apt-get update
apt-get install -y --no-install-recommends \
  openjdk-21-jdk=21.0.2+13-2
EOF

WORKDIR /opt/cartesi/dapp

COPY src/Main.java .
RUN mkdir -p dist && javac -d ./dist/ Main.java

FROM node:20.8.0-bookworm as build-stage

WORKDIR /opt/cartesi/dapp
COPY . .
RUN yarn install && yarn build

# runtime stage: produces final image that will be executed

# Here the image's platform MUST be linux/riscv64.
# Give preference to small base images, which lead to better start-up
# performance when loading the Cartesi Machine.
FROM --platform=linux/riscv64 cartesi/node:20.8.0-jammy-slim

ARG MACHINE_EMULATOR_TOOLS_VERSION=0.12.0
ADD https://github.com/cartesi/machine-emulator-tools/releases/download/v${MACHINE_EMULATOR_TOOLS_VERSION}/machine-emulator-tools-v${MACHINE_EMULATOR_TOOLS_VERSION}.deb /
RUN dpkg -i /machine-emulator-tools-v${MACHINE_EMULATOR_TOOLS_VERSION}.deb \
  && rm /machine-emulator-tools-v${MACHINE_EMULATOR_TOOLS_VERSION}.deb

LABEL io.sunodo.sdk_version=0.3.0
LABEL io.cartesi.rollups.ram_size=128Mi

ARG DEBIAN_FRONTEND=noninteractive
RUN <<EOF
set -e
apt-get update
apt-get install -y --no-install-recommends \
  busybox-static=1:1.30.1-7ubuntu3 \
  openjdk-21-jdk=21.0.2+13-1~22.04.1
rm -rf /var/lib/apt/lists/*
EOF

ENV PATH="/opt/cartesi/bin:${PATH}"

WORKDIR /opt/cartesi/dapp
COPY --from=build-stage /opt/cartesi/dapp/dist .
COPY --from=java-stage /opt/cartesi/dapp/dist .

ENV ROLLUP_HTTP_SERVER_URL="http://127.0.0.1:5004"

ENTRYPOINT ["rollup-init"]
#CMD ["node", "index.js"]
CMD ["java", "Main"]