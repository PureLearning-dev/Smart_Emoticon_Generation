import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Typography, message } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import { createAdminUser, deleteAdminUser, fetchAdminUsers, updateAdminUser } from '@/api/admin'
import type { AdminUser, AdminUserPayload } from '@/types/admin'
import dayjs from 'dayjs'
import { useState } from 'react'

function formatTime(v?: string) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'
}

export default function UsersPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<AdminUser | null>(null)
  const [form] = Form.useForm<AdminUserPayload>()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['users'],
    queryFn: fetchAdminUsers,
  })

  const saveMutation = useMutation({
    mutationFn: (payload: AdminUserPayload) =>
      editing ? updateAdminUser(editing.id, payload) : createAdminUser(payload),
    onSuccess: async () => {
      message.success('保存成功')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '保存失败'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAdminUser,
    onSuccess: async () => {
      message.success('删除成功')
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '删除失败'),
  })

  const columns: ColumnsType<AdminUser> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username', ellipsis: true },
    { title: '昵称', dataIndex: 'nickname', ellipsis: true },
    { title: 'OpenID', dataIndex: 'openid', ellipsis: true, render: (v) => v || '—' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (v: number) => (v === 1 ? '正常' : v === 0 ? '禁用' : String(v ?? '—')),
    },
    {
      title: '类型',
      dataIndex: 'userType',
      width: 100,
      render: (v: number) => (v === 2 ? '管理员' : v === 1 ? '普通用户' : String(v ?? '—')),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatTime },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setEditing(record)
              form.setFieldsValue({ ...record, password: undefined })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          用户管理
        </Typography.Title>
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            form.setFieldsValue({ status: 1, userType: 1 })
            setOpen(true)
          }}
        >
          新增用户
        </Button>
      </Space>
      {isError && (
        <Alert
          type="error"
          showIcon
          message="加载失败"
          description={error instanceof Error ? error.message : String(error)}
          style={{ marginBottom: 16 }}
        />
      )}
      <Table<AdminUser>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={editing ? '编辑用户' : '新增用户'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item label="用户名" name="username" rules={[{ required: !editing, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: !editing, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item label="昵称" name="nickname">
            <Input />
          </Form.Item>
          <Form.Item label="OpenID" name="openid">
            <Input />
          </Form.Item>
          <Form.Item label="头像 URL" name="avatarUrl">
            <Input />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select
              options={[
                { value: 1, label: '正常' },
                { value: 0, label: '禁用' },
              ]}
            />
          </Form.Item>
          <Form.Item label="用户类型" name="userType">
            <Select
              options={[
                { value: 1, label: '普通用户' },
                { value: 2, label: '管理员' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
