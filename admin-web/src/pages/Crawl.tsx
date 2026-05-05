import {
  Alert,
  Button,
  Card,
  Checkbox,
  Image,
  Input,
  Space,
  Statistic,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd'
import { useState } from 'react'
import { extractImageUrls, processManyImages, processOneImage } from '@/api/crawl'
import type { ApiErrorBody, CrawlProcessImageResponse } from '@/types/crawl'

/** 后端在 ai-kore 不可达时返回的 JSON（无 success/url 等成功字段） */
function isGatewayError(x: unknown): x is ApiErrorBody {
  if (typeof x !== 'object' || x === null) return false
  const o = x as Record<string, unknown>
  return (
    'hint' in o ||
    (!('success' in o) && !('results' in o) && ('status' in o || ('error' in o && o.error !== undefined)))
  )
}

export default function CrawlPage() {
  const [singleUrl, setSingleUrl] = useState('')
  const [batchText, setBatchText] = useState('')
  const [pageUrl, setPageUrl] = useState('')
  const [previewUrls, setPreviewUrls] = useState<string[]>([])
  const [selectedUrls, setSelectedUrls] = useState<string[]>([])
  const [loading1, setLoading1] = useState(false)
  const [loadingB, setLoadingB] = useState(false)
  const [extracting, setExtracting] = useState(false)
  const [ingestingSelected, setIngestingSelected] = useState(false)
  const [singleResult, setSingleResult] = useState<CrawlProcessImageResponse | ApiErrorBody | null>(null)
  const [resultRows, setResultRows] = useState<CrawlProcessImageResponse[]>([])
  const [summary, setSummary] = useState<{ total: number; successCount: number } | null>(null)

  const appendResults = (rows: CrawlProcessImageResponse[], replace = false) => {
    setResultRows((old) => (replace ? rows : rows.concat(old)))
    setSummary({ total: rows.length, successCount: rows.filter((item) => item.success).length })
  }

  const runSingle = async () => {
    const url = singleUrl.trim()
    if (!url) {
      message.warning('请输入图片 URL')
      return
    }
    setLoading1(true)
    setSingleResult(null)
    try {
      const data = await processOneImage(url)
      setSingleResult(data)
      if (isGatewayError(data)) {
        message.error(data.error || '请求失败')
      } else if (data.success) {
        appendResults([data])
        message.success('入库成功')
      } else {
        appendResults([data])
        message.warning(data.error || '处理未成功')
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '网络错误')
    } finally {
      setLoading1(false)
    }
  }

  const runBatch = async () => {
    const urls = batchText
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean)
    const unique = [...new Set(urls)]
    if (unique.length === 0) {
      message.warning('请输入至少一行 URL')
      return
    }
    if (unique.length > 100) {
      message.warning('单次最多 100 条 URL')
      return
    }
    setLoadingB(true)
    setResultRows([])
    setSummary(null)
    try {
      const data = await processManyImages(unique)
      if (isGatewayError(data)) {
        message.error(data.error || '批量请求失败')
        return
      }
      setResultRows(data.results ?? [])
      setSummary({ total: data.total, successCount: data.successCount })
      message.success(`完成：成功 ${data.successCount} / ${data.total}`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '网络错误')
    } finally {
      setLoadingB(false)
    }
  }

  const runExtract = async () => {
    const url = pageUrl.trim()
    if (!url) {
      message.warning('请输入网页 URL')
      return
    }
    setExtracting(true)
    setPreviewUrls([])
    setSelectedUrls([])
    try {
      const data = await extractImageUrls(url, 100)
      setPreviewUrls(data.urls || [])
      setSelectedUrls(data.urls || [])
      message.success(`解析到 ${data.total} 张图片`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '解析失败')
    } finally {
      setExtracting(false)
    }
  }

  const ingestUrls = async (urls: string[], replace = false) => {
    const unique = [...new Set(urls.map((s) => s.trim()).filter(Boolean))]
    if (unique.length === 0) {
      message.warning('没有可入库的 URL')
      return
    }
    const data = await processManyImages(unique)
    if (isGatewayError(data)) {
      message.error(data.error || '批量入库失败')
      return
    }
    appendResults(data.results ?? [], replace)
    message.success(`完成：成功 ${data.successCount} / ${data.total}`)
  }

  const runIngestSelected = async () => {
    setIngestingSelected(true)
    try {
      await ingestUrls(selectedUrls, true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '入库失败')
    } finally {
      setIngestingSelected(false)
    }
  }

  const retryOne = async (url: string) => {
    try {
      const data = await processOneImage(url)
      if (isGatewayError(data)) {
        message.error(data.error || '重试失败')
        return
      }
      setResultRows((rows) => rows.map((item) => (item.url === url ? data : item)))
      message.success(data.success ? '重试成功' : data.error || '重试完成')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重试失败')
    }
  }

  const retryFailed = async () => {
    const failedUrls = resultRows.filter((item) => !item.success).map((item) => item.url)
    if (failedUrls.length === 0) {
      message.info('没有失败项')
      return
    }
    await ingestUrls(failedUrls, true)
  }

  const copyText = async (text?: string) => {
    if (!text) return
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  }

  const resultColumns = [
    { title: '原始 URL', dataIndex: 'url', ellipsis: true, width: 260 },
    {
      title: '状态',
      width: 88,
      render: (_: unknown, r: CrawlProcessImageResponse) => (r.success ? '成功' : '失败'),
    },
    {
      title: 'OSS URL',
      dataIndex: 'imageUrl',
      ellipsis: true,
      width: 220,
      render: (url: string) => url || '—',
    },
    { title: 'embeddingId', dataIndex: 'embeddingId', ellipsis: true, width: 220 },
    {
      title: 'OCR 摘要',
      dataIndex: 'ocrText',
      ellipsis: true,
      render: (t: string) => (t ? t.slice(0, 60) : '—'),
    },
    { title: '错误原因', dataIndex: 'error', ellipsis: true, render: (t: string | null) => t || '—' },
    {
      title: '操作',
      fixed: 'right' as const,
      width: 180,
      render: (_: unknown, r: CrawlProcessImageResponse) => (
        <Space>
          <Button type="link" onClick={() => copyText(r.imageUrl || r.url)}>
            复制URL
          </Button>
          <Button type="link" onClick={() => copyText(r.embeddingId)}>
            复制ID
          </Button>
          {!r.success && (
            <Button type="link" onClick={() => retryOne(r.url)}>
              重试
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Typography.Title level={4}>离线入库（爬图）</Typography.Title>
      <Tabs
        items={[
          {
            key: 'one',
            label: '单张 URL',
            children: (
              <Card>
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Input
                    value={singleUrl}
                    onChange={(e) => setSingleUrl(e.target.value)}
                    allowClear
                  />
                  <Button type="primary" loading={loading1} onClick={runSingle}>
                    开始入库
                  </Button>
                  {singleResult && (
                    <div>
                      {isGatewayError(singleResult) ? (
                        <Alert type="error" message={singleResult.error} description={singleResult.message} />
                      ) : (
                        <Alert
                          type={singleResult.success ? 'success' : 'warning'}
                          message={singleResult.success ? '处理完成' : singleResult.error || '失败'}
                          description={
                            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                              {JSON.stringify(singleResult, null, 2)}
                            </pre>
                          }
                        />
                      )}
                    </div>
                  )}
                </Space>
              </Card>
            ),
          },
          {
            key: 'batch',
            label: '批量 URL',
            children: (
              <Card>
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Input.TextArea
                    rows={10}
                    value={batchText}
                    onChange={(e) => setBatchText(e.target.value)}
                  />
                  <Button type="primary" loading={loadingB} onClick={runBatch}>
                    批量入库
                  </Button>
                  {summary && (
                    <Space size="large">
                      <Statistic title="总数" value={summary.total} />
                      <Statistic title="成功" value={summary.successCount} valueStyle={{ color: '#3f8600' }} />
                    </Space>
                  )}
                </Space>
              </Card>
            ),
          },
          {
            key: 'page',
            label: '网页解析',
            children: (
              <Card>
                <Space direction="vertical" style={{ width: '100%' }} size="middle">
                  <Space.Compact style={{ width: '100%' }}>
                    <Input value={pageUrl} onChange={(e) => setPageUrl(e.target.value)} />
                    <Button type="primary" loading={extracting} onClick={runExtract}>
                      解析图片
                    </Button>
                  </Space.Compact>
                  {previewUrls.length > 0 && (
                    <>
                      <Space>
                        <Checkbox
                          checked={selectedUrls.length === previewUrls.length}
                          indeterminate={selectedUrls.length > 0 && selectedUrls.length < previewUrls.length}
                          onChange={(e) => setSelectedUrls(e.target.checked ? previewUrls : [])}
                        >
                          全选
                        </Checkbox>
                        <Button type="primary" loading={ingestingSelected} onClick={runIngestSelected}>
                          入库选中图片
                        </Button>
                      </Space>
                      <Table<{ url: string }>
                        size="small"
                        rowKey="url"
                        dataSource={previewUrls.map((url) => ({ url }))}
                        pagination={{ pageSize: 10 }}
                        rowSelection={{
                          selectedRowKeys: selectedUrls,
                          onChange: (keys) => setSelectedUrls(keys.map(String)),
                        }}
                        columns={[
                          {
                            title: '预览',
                            dataIndex: 'url',
                            width: 96,
                            render: (url: string) => <Image src={url} width={56} height={56} style={{ objectFit: 'cover' }} />,
                          },
                          { title: '图片 URL', dataIndex: 'url', ellipsis: true },
                        ]}
                      />
                    </>
                  )}
                </Space>
              </Card>
            ),
          },
        ]}
      />
      {summary && (
        <Space size="large" style={{ marginTop: 16 }}>
          <Statistic title="总数" value={summary.total} />
          <Statistic title="成功" value={summary.successCount} valueStyle={{ color: '#3f8600' }} />
          <Button onClick={retryFailed}>重试全部失败项</Button>
        </Space>
      )}
      {resultRows.length > 0 && (
        <Table<CrawlProcessImageResponse>
          style={{ marginTop: 16 }}
          size="small"
          rowKey={(r) => r.url + (r.embeddingId || '')}
          dataSource={resultRows}
          pagination={{ pageSize: 8 }}
          scroll={{ x: 1300 }}
          columns={resultColumns}
        />
      )}
    </div>
  )
}
