# Set up a base image
FROM phusion/baseimage:0.9.20

# Install OpenJDK from zulu.org and update system
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0x219BD9C9 \
 && (echo "deb http://repos.azulsystems.com/ubuntu stable main" >> /etc/apt/sources.list.d/zulu.list)
RUN apt-get -qq update \
 && apt-get -y upgrade
RUN apt-get -qqy install zulu-8 ntp

# Set up project path /opt/corda directory
ENV PROJECT_PATH=/opt/src
WORKDIR /opt/src

# Copy corda jar
# ADD {remote-location-for-corda-jar} /opt/corda/corda.jar
# Currently, use the local version of corda
COPY corda.jar $PROJECT_PATH/corda.jar

### Init script for corda
RUN mkdir /etc/service/corda


# Expose ports for corda (depends on how many we use, too)
EXPOSE 10002
# can have multiple exposed ports

# Deploy the nodes
CMD ["$WORKDIR/gradlew", "samples:trader-demo:deployNodes"]

# should sleep for an arbitrary amount of time

# Run the nodes
CMD ["$WORKDIR/samples/trader-demo/build/nodes/runnodes.sh"]