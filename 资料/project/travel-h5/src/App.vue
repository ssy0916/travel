<template>
  <div class="app-wrapper">
    <router-view />
    <van-tabbar route>
      <van-tabbar-item icon="home-o" to="/">首页</van-tabbar-item>
      <van-tabbar-item icon="chat-o" to="/chat">聊天</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">个人中心</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.app-wrapper {
  padding-bottom: 50px;
}
</style>
