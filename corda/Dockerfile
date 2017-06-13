FROM ubuntu:14.04
ENV PROJECT_PATH=/opt/src/

RUN apt-get update
RUN apt-get install -y \
    git-core \
    software-properties-common \
    vim \
    curl

RUN add-apt-repository ppa:webupd8team/java \
	apt-get update

RUN apt-get install git curl oracle-java7-installer

COPY . ${PROJECT_PATH}

RUN cd ${PROJECT_PATH} && \
	ls

CMD ["./gradlew samples:trader-demo:deployNodes"]