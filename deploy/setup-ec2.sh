#!/usr/bin/env bash
# EC2(Ubuntu 24.04) 최초 1회 설정: Docker 설치 + 2GB 스왑 + 배포 디렉터리 준비
# 사용법: ssh 접속 후 `bash setup-ec2.sh`
set -euo pipefail

# 1. Docker Engine + compose 플러그인
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# 2. 스왑 2GB (t3.small 2GB 메모리 보완 - JVM/빌드 순간 메모리 급증 대비)
if ! sudo swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
  sudo sysctl --system >/dev/null
fi

# 3. 배포 디렉터리 (.env 는 직접 작성: deploy/.env.example 참고)
mkdir -p ~/wiki

echo "완료. 다시 로그인해야 docker 그룹 권한이 적용됩니다."
echo "다음 단계: ~/wiki/.env 작성 → GitHub Actions 배포 실행"
