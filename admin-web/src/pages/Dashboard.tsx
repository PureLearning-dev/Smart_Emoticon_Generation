import { fetchAdminStats } from '@/api/admin'
import type { AdminStats } from '@/types/admin'
import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Row, Space, Spin, Statistic, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'

function statValue(v: number | undefined) {
  return v === undefined || Number.isNaN(v) ? '—' : v
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery<AdminStats>({
    queryKey: ['admin-stats'],
    queryFn: fetchAdminStats,
  })

  return (
    <div>
      <Space align="center" style={{ marginBottom: 8 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          仪表盘
        </Typography.Title>
        <Button
          type="default"
          icon={<ReloadOutlined />}
          loading={isFetching}
          onClick={() => void refetch()}
        >
          刷新统计
        </Button>
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
        以下为当前数据库各模块记录总数（来自 <Typography.Text code>GET /api/admin/stats</Typography.Text>
        ）。点击卡片可进入对应管理页。
      </Typography.Paragraph>

      {isError ? (
        <Alert
          type="error"
          showIcon
          message="加载统计失败"
          description={error instanceof Error ? error.message : String(error)}
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <Spin spinning={isLoading && !data}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card hoverable onClick={() => navigate('/users')}>
              <Statistic title="用户总数（users）" value={statValue(data?.userTotal)} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card hoverable onClick={() => navigate('/generated-images')}>
              <Statistic title="用户生成图（user_generated_images）" value={statValue(data?.generatedImageTotal)} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card hoverable onClick={() => navigate('/plaza')}>
              <Statistic title="广场内容（plaza_contents）" value={statValue(data?.plazaContentTotal)} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card hoverable onClick={() => navigate('/plaza')}>
              <Statistic title="广场文章详情（plaza_articles）" value={statValue(data?.plazaArticleTotal)} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card hoverable onClick={() => navigate('/crawl')}>
              <Statistic title="素材库表情（meme_assets，与离线入库相关）" value={statValue(data?.memeAssetTotal)} />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card>
              <Statistic title="用户收藏（user_favorites）" value={statValue(data?.userFavoriteTotal)} />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  )
}
