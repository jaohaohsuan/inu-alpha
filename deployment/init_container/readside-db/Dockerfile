FROM alpine:edge

RUN apk --no-cache add ansible
ADD hosts /etc/ansible/hosts
ADD playbook.yaml /playbook.yaml
CMD ["ansible-playbook","playbook.yaml"]