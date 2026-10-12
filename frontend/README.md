# MealGo 管理端

`dist/` 来自现有课程管理端的编译产物，本轮增加了 MealGo 品牌资产、登录页样式与按当前域名连接的 WebSocket。它不是重新实现的 Vue 源码工程，也没有可执行的 `npm run build`。

本次可维护的视觉改动集中在 `dist/brand/`；原有打包 JS 中仅替换品牌字符串、资产路径与 WebSocket 地址。打包产物的业务框架仍来自课程，来源与边界见根目录 `THIRD_PARTY.md`。

本机 Nginx：把 `dist/` 的内容复制到 Nginx 的 `html/mealgo/`，使用 `nginx.local.conf` 的配置内容，然后执行 `nginx -t` 与 `nginx -s reload`。保留原 `conf/mime.types`。

Docker Compose：自动挂载 `dist/` 并使用 `nginx.docker.conf`。访问地址默认是 `http://localhost:8081`。
