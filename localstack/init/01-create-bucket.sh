#!/bin/bash
# LocalStack 기동 시 로컬 개발용 S3 버킷을 생성한다.
# 버킷 이름은 application-local.yml의 app.aws.s3.bucket / spring.cloud.aws.s3.bucket과 일치해야 한다.
set -e

awslocal s3 mb s3://dondok-bucket --region ap-northeast-2 || true

echo "LocalStack init: dondok-bucket ready."