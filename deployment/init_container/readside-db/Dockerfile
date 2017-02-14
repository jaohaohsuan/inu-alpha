FROM alpine:edge

RUN apk --no-cache add ansible
ADD hosts /etc/ansible/hosts
ADD playbook.yaml /playbook.yaml
ADD files /files
ADD templates /templates
ENV ELASTICSEARCH_SERVICE_HOST=127.0.0.1
CMD ["ansible-playbook","playbook.yaml"]