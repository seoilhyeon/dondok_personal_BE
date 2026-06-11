#!/bin/bash
# LocalStack 기동 시 로컬 개발용 S3 버킷을 생성한다.
# 버킷 이름은 application-local.yml의 app.aws.s3.bucket / spring.cloud.aws.s3.bucket과 일치해야 한다.
set -e

awslocal s3 mb s3://dondok-bucket --region ap-northeast-2 || true

# 브라우저가 presigned URL로 S3(LocalStack)에 직접 PUT 업로드하려면 버킷 CORS가 필요하다.
# FE(:3000) -> S3(:4566)는 cross-origin이라 버킷 CORS가 없으면 브라우저가 preflight/PUT을 차단한다.
# (백엔드 app.cors 설정은 Spring API에만 적용되고 S3 엔드포인트에는 적용되지 않는다.)
awslocal s3api put-bucket-cors --bucket dondok-bucket --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["http://localhost:3000", "http://127.0.0.1:3000"],
      "AllowedMethods": ["PUT", "GET", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

echo "LocalStack init: dondok-bucket ready (with CORS)."