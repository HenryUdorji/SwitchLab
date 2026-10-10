FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 switchlab
WORKDIR /opt/switchlab
COPY app/build/libs/switchlab.jar switchlab.jar
USER switchlab
ENV SWITCHLAB_Q2_DEPLOY_DIR=/tmp/switchlab-q2/deploy
EXPOSE 8080 5000 5001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "switchlab.jar"]
