# JavaSqlAdmin - 一个运营环境数据查询系统

## 项目介绍

所有企业都面临的一个需求就是需要开发人员连接线上生产库，但又担心开发人员查询线上敏感数据甚至拖库。一般做法都是：

1. 通过限制查询人员、限制查询的表和字段。
2. 使用跳板机，所有查询都要在跳板机上进行。进出数据文件要过审查机制。
3. 使用第三方系统，记录每人的查询语句。

从上向下规则是越来越严。第一级直接由DBA进行查询账号权限限制即可，第二级推荐使用开源的[JumpServer](https://github.com/jumpserver)。第三级我在网上搜了下发现基本都是基于WEB系统，查询语句入库。但能支持的数据库只有MySql没有发现开源的能只是微软家SqlServer的。本项目就是在这个基础上开始准备开发的。

## 项目部署

本项目使用Reactjs+Springboot+mysql的组合。最简项目运行可以使用Docker来运行。

```shell
# 首先下载数据库初始化脚本 
wget https://github.com/guohai163/java-sql-admin/raw/master/script/init.sql
# 按初始化脚本编辑修改.sql文件。
vim init.sql
# 启动数据库容器
docker run --name mariadb -v /opt/java-sql-admin/script:/docker-entrypoint-initdb.d -e  MYSQL_ROOT_PASSWORD=my-secret-pw -d mariadb:10
# 启动javasqladmin容器
 docker run --name javasqladmin -d --link mariadb -p 80:8002 gcontainer/java-sql-admin:0.3.0 
# 使用浏览器访问 
open http://localhost
```