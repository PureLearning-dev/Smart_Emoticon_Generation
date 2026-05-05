import { Card, Col, Row, Statistic, Typography } from 'antd'

export default function DashboardPage() {
  return (
    <div>
      <Typography.Title level={4}>仪表盘</Typography.Title>
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={6}>
          <Card>
            <Statistic title="用户管理" value="users" />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card>
            <Statistic title="生成图片" value="generated" />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card>
            <Statistic title="广场文章" value="plaza" />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card>
            <Statistic title="离线入库" value="crawl" />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
