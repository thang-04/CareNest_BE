FROM quay.io/keycloak/keycloak:26.8.0
COPY extensions/carenest-auth/target/carenest-auth-1.0.0.jar /opt/keycloak/providers/carenest-auth.jar
RUN /opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true
