FROM debian:bookworm-slim

WORKDIR /opt/trove
COPY server/trove-server .

RUN chmod +x ./trove-server

CMD ["./trove-server"]
