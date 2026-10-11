<template>
  <div class="page">
    <van-nav-bar title="常用出行人" left-arrow @click-left="onBack" safe-area-inset-top>
      <template #right>
        <van-icon name="plus" size="18" @click="openAdd" />
      </template>
    </van-nav-bar>

    <div class="content">
      <van-empty v-if="contacts.length === 0" description="还没有添加出行人">
        <van-button type="primary" round size="small" @click="openAdd">添加出行人</van-button>
      </van-empty>

      <van-swipe-cell v-for="c in contacts" :key="c.id">
        <div class="contact-card" @click="openEdit(c)">
          <div class="contact-head">
            <span class="name">{{ c.name }}</span>
            <span v-if="c.isDefault" class="default-tag">默认</span>
          </div>
          <div class="contact-line">{{ maskPhone(c.phone) }}</div>
          <div class="contact-line">{{ maskIdCard(c.idCard) }}</div>
        </div>
        <template #right>
          <van-button square type="danger" text="删除" class="swipe-btn" @click="removeContact(c.id)" />
        </template>
      </van-swipe-cell>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <van-popup v-model:show="showForm" round position="bottom">
      <div class="form-title">{{ editingId ? '编辑出行人' : '添加出行人' }}</div>
      <van-field v-model="form.name" label="姓名" label-width="70px" placeholder="请输入姓名" clearable />
      <van-field
        v-model="form.phone"
        label="手机号"
        label-width="70px"
        type="tel"
        maxlength="11"
        placeholder="请输入手机号"
        clearable
      />
      <van-field
        v-model="form.idCard"
        label="身份证号"
        label-width="70px"
        maxlength="18"
        placeholder="请输入身份证号"
        clearable
      />
      <van-cell center title="设为默认出行人" class="default-cell">
        <template #right-icon>
          <van-switch v-model="form.isDefault" size="22" />
        </template>
      </van-cell>
      <div class="form-actions">
        <van-button type="primary" block round @click="save">保存</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { contactStore } from '../utils/storage.js'

const router = useRouter()
const contacts = ref(contactStore.list())

const showForm = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', phone: '', idCard: '', isDefault: false })

const onBack = () => router.back()

const resetForm = () => {
  form.name = ''
  form.phone = ''
  form.idCard = ''
  form.isDefault = false
}

const openAdd = () => {
  editingId.value = null
  resetForm()
  showForm.value = true
}

const openEdit = (c) => {
  editingId.value = c.id
  form.name = c.name
  form.phone = c.phone
  form.idCard = c.idCard
  form.isDefault = !!c.isDefault
  showForm.value = true
}

const save = () => {
  const name = form.name.trim()
  if (!name) return showToast('请输入姓名')
  if (!/^1\d{10}$/.test(form.phone)) return showToast('请输入正确的手机号')
  if (!/^\d{17}[\dXx]$/.test(form.idCard)) return showToast('请输入正确的身份证号')

  if (editingId.value) {
    contactStore.update(editingId.value, {
      name,
      phone: form.phone,
      idCard: form.idCard.toUpperCase(),
      isDefault: form.isDefault
    })
    showToast('保存成功')
  } else {
    contactStore.add({
      name,
      phone: form.phone,
      idCard: form.idCard.toUpperCase(),
      isDefault: form.isDefault
    })
    showToast('添加成功')
  }
  contacts.value = contactStore.list()
  showForm.value = false
}

const removeContact = (id) => {
  showConfirmDialog({ title: '提示', message: '确定删除该出行人吗？' })
    .then(() => {
      contactStore.remove(id)
      contacts.value = contactStore.list()
      showToast('已删除')
    })
    .catch(() => {})
}

const maskPhone = (phone) => phone ? phone.slice(0, 3) + '****' + phone.slice(7) : ''
const maskIdCard = (id) => id ? id.slice(0, 3) + '***********' + id.slice(14) : ''
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: var(--paper-bg);
}
.content {
  padding: 12px;
}
.contact-card {
  background: var(--paper-white);
  border: 1px solid var(--paper-line);
  border-radius: var(--radius-paper);
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-paper);
  transition: transform var(--duration-stamp) ease;
}
.contact-card:active {
  transform: scale(0.97);
}
.contact-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  font-family: var(--font-serif);
}
.default-tag {
  font-size: 10px;
  color: var(--stamp-red);
  background: transparent;
  border: 1px solid var(--stamp-red);
  border-radius: var(--radius-photo);
  padding: 1px 6px;
  font-family: var(--font-serif);
}
.contact-line {
  margin-top: 6px;
  font-size: 13px;
  color: var(--van-text-color-3);
}
.swipe-btn {
  height: 100%;
}
.form-title {
  padding: 20px 16px 12px;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
  color: var(--ink);
  font-family: var(--font-serif);
}
.default-cell {
  margin-top: 4px;
}
.form-actions {
  padding: 20px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
}
</style>
