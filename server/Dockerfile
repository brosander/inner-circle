from openjdk:8-jre-alpine

RUN addgroup -g 8675 circle && \
    adduser -D -u 8675 -G circle circle

COPY --chown=circle:circle . /opt/inner-circle

ENTRYPOINT ["/opt/inner-circle/bin/server"]