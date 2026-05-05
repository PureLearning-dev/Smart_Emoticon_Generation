import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Typography, message } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useState } from 'react'
import {
  createPlazaArticle,
  createPlazaContent,
  deletePlazaArticle,
  deletePlazaContent,
  fetchPlazaArticles,
  fetchPlazaContents,
  updatePlazaArticle,
  updatePlazaContent,
} from '@/api/admin'
import type { PlazaArticle, PlazaArticlePayload, PlazaContent, PlazaContentPayload } from '@/types/admin'

function formatTime(v?: string) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'
}

export default function PlazaManagePage() {
  return (
    <div>
      <Typography.Title level={4}>广场内容管理</Typography.Title>
      <Tabs
        items={[
          { key: 'content', label: '内容', children: <PlazaContentTable /> },
          { key: 'article', label: '文章详情', children: <PlazaArticleTable /> },
        ]}
      />
    </div>
  )
}

function PlazaContentTable() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PlazaContent | null>(null)
  const [form] = Form.useForm<PlazaContentPayload>()
  const { data, isLoading } = useQuery({ queryKey: ['plaza-contents'], queryFn: fetchPlazaContents })

  const saveMutation = useMutation({
    mutationFn: (payload: PlazaContentPayload) =>
      editing ? updatePlazaContent(editing.id, payload) : createPlazaContent(payload),
    onSuccess: async () => {
      message.success('保存成功')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await queryClient.invalidateQueries({ queryKey: ['plaza-contents'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '保存失败'),
  })

  const deleteMutation = useMutation({
    mutationFn: deletePlazaContent,
    onSuccess: async () => {
      message.success('删除成功')
      await queryClient.invalidateQueries({ queryKey: ['plaza-contents'] })
      await queryClient.invalidateQueries({ queryKey: ['plaza-articles'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '删除失败'),
  })

  const openEditor = (record?: PlazaContent) => {
    setEditing(record ?? null)
    form.resetFields()
    form.setFieldsValue(record ?? { contentType: 2, status: 1, sortOrder: 0 })
    setOpen(true)
  }

  const columns: ColumnsType<PlazaContent> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'contentType',
      width: 90,
      render: (v: number) => (v === 2 ? '文章' : v === 1 ? '表情包' : String(v ?? '—')),
    },
    { title: '标签', dataIndex: 'tagName', width: 120, ellipsis: true },
    { title: '排序', dataIndex: 'sortOrder', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => (v === 1 ? '上架' : '下架'),
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
          <Popconfirm title="确认删除该内容及关联文章？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space style={{ width: '100%', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" onClick={() => openEditor()}>
          新增内容
        </Button>
      </Space>
      <Table<PlazaContent>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={editing ? '编辑内容' : '新增内容'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item label="标题" name="title" rules={[{ required: !editing, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="类型" name="contentType">
            <Select options={[{ value: 2, label: '文章' }, { value: 1, label: '表情包' }]} />
          </Form.Item>
          <Form.Item label="摘要" name="summary">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="封面 URL" name="coverUrl">
            <Input />
          </Form.Item>
          <Form.Item label="标签" name="tagName">
            <Input />
          </Form.Item>
          <Form.Item label="关联素材 ID" name="refMemeAssetId">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="文章外链" name="articleUrl">
            <Input />
          </Form.Item>
          <Form.Item label="排序权重" name="sortOrder">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select options={[{ value: 1, label: '上架' }, { value: 0, label: '下架' }]} />
          </Form.Item>
          <Form.Item label="创建人 ID" name="createUserId">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}

function PlazaArticleTable() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PlazaArticle | null>(null)
  const [form] = Form.useForm<PlazaArticlePayload>()
  const { data, isLoading } = useQuery({ queryKey: ['plaza-articles'], queryFn: fetchPlazaArticles })

  const saveMutation = useMutation({
    mutationFn: (payload: PlazaArticlePayload) =>
      editing ? updatePlazaArticle(editing.id, payload) : createPlazaArticle(payload),
    onSuccess: async () => {
      message.success('保存成功')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await queryClient.invalidateQueries({ queryKey: ['plaza-articles'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '保存失败'),
  })

  const deleteMutation = useMutation({
    mutationFn: deletePlazaArticle,
    onSuccess: async () => {
      message.success('删除成功')
      await queryClient.invalidateQueries({ queryKey: ['plaza-articles'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '删除失败'),
  })

  const openEditor = (record?: PlazaArticle) => {
    setEditing(record ?? null)
    form.resetFields()
    form.setFieldsValue(record ?? { readCount: 0, likeCount: 0, status: 1 })
    setOpen(true)
  }

  const columns: ColumnsType<PlazaArticle> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '内容ID', dataIndex: 'plazaContentId', width: 100 },
    { title: '作者', dataIndex: 'authorName', width: 120, ellipsis: true },
    { title: '来源', dataIndex: 'sourceName', width: 140, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number) => (v === 1 ? '发布' : '下线'),
    },
    { title: '阅读', dataIndex: 'readCount', width: 90 },
    { title: '点赞', dataIndex: 'likeCount', width: 90 },
    { title: '发布时间', dataIndex: 'publishTime', width: 180, render: formatTime },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => openEditor(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该文章详情？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Space style={{ width: '100%', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" onClick={() => openEditor()}>
          新增文章
        </Button>
      </Space>
      <Table<PlazaArticle>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data ?? []}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={editing ? '编辑文章' : '新增文章'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        width={820}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item label="内容 ID" name="plazaContentId" rules={[{ required: !editing, message: '请输入内容 ID' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="正文" name="contentBody" rules={[{ required: !editing, message: '请输入正文' }]}>
            <Input.TextArea rows={8} />
          </Form.Item>
          <Form.Item label="作者" name="authorName">
            <Input />
          </Form.Item>
          <Form.Item label="来源" name="sourceName">
            <Input />
          </Form.Item>
          <Form.Item label="来源 URL" name="sourceUrl">
            <Input />
          </Form.Item>
          <Form.Item label="阅读数" name="readCount">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="点赞数" name="likeCount">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="状态" name="status">
            <Select options={[{ value: 1, label: '发布' }, { value: 0, label: '下线' }]} />
          </Form.Item>
          <Form.Item label="发布时间" name="publishTime">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
