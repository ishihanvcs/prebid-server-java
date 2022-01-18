FROM adoptopenjdk/openjdk15

WORKDIR /

COPY ./target/prebid-server*.jar prebid-server.jar
COPY ./improvedigital/stored-data stored-data
COPY ./improvedigital/config config

EXPOSE 8080
ENTRYPOINT ["java","-Dlogging.config=/config/logging.xml","-Dlog4j2.formatMsgNoLookups=true","-jar","/prebid-server.jar", "--spring.config.additional-location=/config/application.yaml"]
