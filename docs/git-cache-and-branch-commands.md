# 깃 추적 캐시를 모두 비우는 명령어 (실제 파일은 안 지워지니 안심해도 돼)
git rm -r --cached .
git add .
git commit -m "chore: gitignore 세팅 및 캐시 정리"


# 깃허브 브랜치 변경 & 합병
git checkout develop
git pull origin develop
git checkout 내브랜치
git merge develop
