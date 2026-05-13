import { Button, Form, Image, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Typography, message } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useState } from 'react'
import {
  createGeneratedImage,
  deleteGeneratedImage,
  fetchGeneratedImages,
  fetchUserDisplayName,
  updateGeneratedImage,
} from '@/api/admin'
import type { GeneratedImage, GeneratedImagePayload } from '@/types/admin'

function formatTime(v?: string) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'
}

export default function GeneratedImagesPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<GeneratedImage | null>(null)
  const [form] = Form.useForm<GeneratedImagePayload>()
  const { data, isLoading } = useQuery({
    queryKey: ['generated-images'],
    queryFn: fetchGeneratedImages,
  })
  const userIds = [...new Set((data ?? []).map((item) => item.userId).filter((id): id is number => typeof id === 'number'))]
  const { data: userNames, isLoading: userNamesLoading } = useQuery({
    queryKey: ['generated-image-user-names', userIds.join(',')],
    queryFn: async () => Promise.all(userIds.map((id) => fetchUserDisplayName(id))),
    enabled: userIds.length > 0,
  })

  const userNameMap = new Map(
    (userNames ?? []).map((item) => [item.userId, item.displayName]),
  )

  const saveMutation = useMutation({
    mutationFn: (payload: GeneratedImagePayload) =>
      editing ? updateGeneratedImage(editing.id, payload) : createGeneratedImage(payload),
    onSuccess: async () => {
      message.success('保存成功')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await queryClient.invalidateQueries({ queryKey: ['generated-images'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '保存失败'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteGeneratedImage,
    onSuccess: async () => {
      message.success('删除成功')
      await queryClient.invalidateQueries({ queryKey: ['generated-images'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '删除失败'),
  })

  const openEditor = (record?: GeneratedImage) => {
    setEditing(record ?? null)
    form.resetFields()
    form.setFieldsValue(record ?? { generationStatus: 1, isPublic: 0 })
    setOpen(true)
  }

  const columns: ColumnsType<GeneratedImage> = [
    {
      title: '用户名称',
      dataIndex: 'userId',
      width: 140,
      ellipsis: true,
      render: (userId?: number) => (userId ? userNameMap.get(userId) || `用户 ${userId}` : '—'),
    },
    {
      title: '图片',
      dataIndex: 'generatedImageUrl',
      width: 96,
      render: (url?: string) => (url ? <Image src={url} width={56} height={56} style={{ objectFit: 'cover' }} /> : '—'),
    },
    { title: '提示词', dataIndex: 'promptText', ellipsis: true },
    { title: '场景', dataIndex: 'usageScenario', width: 120, ellipsis: true },
    { title: '风格', dataIndex: 'styleTag', width: 100, ellipsis: true },
    {
      title: '公开',
      dataIndex: 'isPublic',
      width: 80,
      render: (v: number) => (v === 1 ? '公开' : '私有'),
    },
    {
      title: '状态',
      dataIndex: 'generationStatus',
      width: 90,
      render: (v: number) => (v === 1 ? '成功' : v === 2 ? '处理中' : '失败'),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: formatTime },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => openEditor(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该生成图片记录？" onConfirm={() => deleteMutation.mutate(record.id)}>
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
          用户生成图片
        </Typography.Title>
        <Button type="primary" onClick={() => openEditor()}>
          新增记录
        </Button>
      </Space>
      <Table<GeneratedImage>
        rowKey="id"
        loading={isLoading || userNamesLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1240 }}
      />
      <Modal
        title={editing ? '编辑生成图片' : '新增生成图片'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item label="用户 ID" name="userId" rules={[{ required: !editing, message: '请输入用户 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="生成图 URL" name="generatedImageUrl" rules={[{ required: !editing, message: '请输入生成图 URL' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="提示词" name="promptText">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="生成文案" name="generatedText">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="参考图 URL" name="sourceImageUrl">
            <Input />
          </Form.Item>
          <Form.Item label="来源素材 ID" name="sourceMemeAssetId">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="场景" name="usageScenario">
            <Input />
          </Form.Item>
          <Form.Item label="风格" name="styleTag">
            <Input />
          </Form.Item>
          <Form.Item label="embeddingId" name="embeddingId">
            <Input />
          </Form.Item>
          <Form.Item label="生成状态" name="generationStatus">
            <Select
              options={[
                { value: 1, label: '成功' },
                { value: 2, label: '处理中' },
                { value: 0, label: '失败' },
              ]}
            />
          </Form.Item>
          <Form.Item label="是否公开" name="isPublic">
            <Select
              options={[
                { value: 1, label: '公开' },
                { value: 0, label: '私有' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
