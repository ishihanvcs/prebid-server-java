FROM adoptopenjdk/openjdk15
WORKDIR /
COPY ./target/prebid-server*.jar prebid-server.jar
COPY stored-data stored-data
COPY config config
EXPOSE 8080
ENTRYPOINT ["java","-Dlogging.config=/config/azerion-logging.xml","-Dlog4j2.formatMsgNoLookups=true","-jar","/prebid-server.jar", "--spring.config.additional-location=/config/azerion-config.yaml"]
