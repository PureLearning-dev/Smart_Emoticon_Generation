import {
  Alert,
  Button,
  Form,
  Image,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useState } from 'react'
import {
  createMemeAsset,
  deleteMemeAsset,
  fetchMemeAssets,
  updateMemeAsset,
} from '@/api/admin'
import type { MemeAssetPayload, MemeAssetRow } from '@/types/admin'

function formatTime(v?: string) {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'
}

export default function MemeAssetsPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<MemeAssetRow | null>(null)
  const [form] = Form.useForm<MemeAssetPayload>()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [filterStatus, setFilterStatus] = useState<number | undefined>(undefined)
  const [filterSourceType, setFilterSourceType] = useState<number | undefined>(undefined)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['meme-assets', page, pageSize, keyword, filterStatus, filterSourceType],
    queryFn: () =>
      fetchMemeAssets({
        page,
        size: pageSize,
        keyword: keyword || undefined,
        status: filterStatus,
        sourceType: filterSourceType,
      }),
  })

  const saveMutation = useMutation({
    mutationFn: (payload: MemeAssetPayload) =>
      editing ? updateMemeAsset(editing.id, payload) : createMemeAsset(payload),
    onSuccess: async () => {
      message.success('保存成功')
      setOpen(false)
      setEditing(null)
      form.resetFields()
      await queryClient.invalidateQueries({ queryKey: ['meme-assets'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '保存失败'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteMemeAsset,
    onSuccess: async () => {
      message.success('删除成功')
      await queryClient.invalidateQueries({ queryKey: ['meme-assets'] })
      await queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (e) => message.error(e instanceof Error ? e.message : '删除失败'),
  })

  const openEditor = (record?: MemeAssetRow) => {
    setEditing(record ?? null)
    form.resetFields()
    form.setFieldsValue(
      record ?? {
        status: 1,
        isPublic: 1,
        sourceType: 1,
      },
    )
    setOpen(true)
  }

  const columns: ColumnsType<MemeAssetRow> = [
    { title: 'ID', dataIndex: 'id', width: 72, fixed: 'left' },
    {
      title: '预览',
      dataIndex: 'fileUrl',
      width: 88,
      fixed: 'left',
      render: (url?: string) =>
        url ? (
          <Image src={url} width={56} height={56} style={{ objectFit: 'cover' }} alt="" />
        ) : (
          '—'
        ),
    },
    { title: '标题', dataIndex: 'title', width: 140, ellipsis: true },
    { title: '场景', dataIndex: 'usageScenario', width: 100, ellipsis: true },
    { title: '风格', dataIndex: 'styleTag', width: 88, ellipsis: true },
    {
      title: 'OCR',
      dataIndex: 'ocrText',
      width: 160,
      ellipsis: true,
      render: (t?: string) => t || '—',
    },
    { title: 'embeddingId', dataIndex: 'embeddingId', width: 140, ellipsis: true },
    {
      title: '来源类型',
      dataIndex: 'sourceType',
      width: 96,
      render: (v?: number) => (v === 2 ? '用户成品' : v === 1 ? '系统采集' : String(v ?? '—')),
    },
    { title: '来源', dataIndex: 'source', width: 120, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 72,
      render: (v?: number) => (v === 1 ? '正常' : v === 0 ? '下架' : String(v ?? '—')),
    },
    {
      title: '公开',
      dataIndex: 'isPublic',
      width: 72,
      render: (v?: number) => (v === 1 ? '是' : v === 0 ? '否' : String(v ?? '—')),
    },
    { title: '创建时间', dataIndex: 'createTime', width: 168, render: formatTime },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => openEditor(record)}>
            编辑
          </Button>
          <Popconfirm title="确认删除该条爬虫素材？" onConfirm={() => deleteMutation.mutate(record.id)}>
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
          爬取素材（meme_assets）
        </Typography.Title>
        <Button type="primary" onClick={() => openEditor()}>
          新增
        </Button>
      </Space>
      <Space wrap style={{ marginBottom: 16 }} align="start">
        <Input.Search
          allowClear
          placeholder="标题 / OCR / embeddingId / 来源"
          style={{ width: 280 }}
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onSearch={(v) => {
            setKeyword((v || '').trim())
            setPage(1)
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          value={filterStatus}
          onChange={(v) => {
            setFilterStatus(v === undefined ? undefined : Number(v))
            setPage(1)
          }}
          options={[
            { value: 1, label: '正常' },
            { value: 0, label: '下架' },
          ]}
        />
        <Select
          allowClear
          placeholder="来源类型"
          style={{ width: 140 }}
          value={filterSourceType}
          onChange={(v) => {
            setFilterSourceType(v === undefined ? undefined : Number(v))
            setPage(1)
          }}
          options={[
            { value: 1, label: '系统采集' },
            { value: 2, label: '用户成品' },
          ]}
        />
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
        展示离线入库/爬虫写入的表情包主数据；删除仅删除 MySQL 记录，不自动删除 Milvus 向量，如需一致请另行运维。
      </Typography.Paragraph>
      {isError && (
        <Alert type="error" showIcon message="加载失败" description={error instanceof Error ? error.message : '未知错误'} style={{ marginBottom: 16 }} />
      )}
      <Table<MemeAssetRow>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.records ?? []}
        scroll={{ x: 1400 }}
        pagination={{
          current: page,
          pageSize,
          total: data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => {
            setPage(p)
            setPageSize(ps)
          },
        }}
      />

      <Modal
        title={editing ? `编辑素材 #${editing.id}` : '新增素材'}
        open={open}
        onCancel={() => {
          setOpen(false)
          setEditing(null)
          form.resetFields()
        }}
        width={720}
        destroyOnClose
        footer={null}
      >
        <Form<MemeAssetPayload>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item name="title" label="标题">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item name="fileUrl" label="图片 URL（OSS）" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="thumbnailUrl" label="缩略图 URL">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item name="embeddingId" label="embeddingId（与 Milvus 一致）">
            <Input placeholder="可选；填写时须全局唯一" />
          </Form.Item>
          <Form.Item name="usageScenario" label="使用场景">
            <Input placeholder="如：职场、日常" />
          </Form.Item>
          <Form.Item name="styleTag" label="风格标签">
            <Input placeholder="如：搞笑、治愈" />
          </Form.Item>
          <Form.Item name="ocrText" label="OCR 文本">
            <Input.TextArea rows={3} placeholder="图中文字" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="contentText" label="统一语义文本">
            <Input.TextArea rows={2} placeholder="检索/展示扩展用" />
          </Form.Item>
          <Form.Item name="source" label="来源标识">
            <Input placeholder="爬虫站点或备注" />
          </Form.Item>
          <Form.Item name="sourceType" label="来源类型">
            <Select
              options={[
                { value: 1, label: '1 系统采集' },
                { value: 2, label: '2 用户创作成品' },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 1, label: '正常' },
                { value: 0, label: '下架' },
              ]}
            />
          </Form.Item>
          <Form.Item name="isPublic" label="是否公开">
            <Select
              options={[
                { value: 1, label: '公开' },
                { value: 0, label: '私有' },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={saveMutation.isPending}>
                保存
              </Button>
              <Button
                onClick={() => {
                  setOpen(false)
                  setEditing(null)
                  form.resetFields()
                }}
              >
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
