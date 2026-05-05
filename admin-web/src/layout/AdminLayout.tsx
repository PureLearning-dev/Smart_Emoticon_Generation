import { Layout, Menu, theme } from 'antd'
import {
  CloudDownloadOutlined,
  DashboardOutlined,
  FileTextOutlined,
  PictureOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearAuthSession } from '@/config/auth'

const { Header, Sider, Content } = Layout

export default function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = theme.useToken()

  const selected = (() => {
    if (location.pathname.startsWith('/users')) return ['/users']
    if (location.pathname.startsWith('/generated-images')) return ['/generated-images']
    if (location.pathname.startsWith('/plaza')) return ['/plaza']
    if (location.pathname.startsWith('/crawl')) return ['/crawl']
    return ['/']
  })()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0}>
        <div
          style={{
            height: 64,
            margin: 16,
            color: '#fff',
            fontWeight: 600,
            fontSize: 15,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          表情包管理
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selected}
          items={[
            { key: '/', icon: <DashboardOutlined />, label: '仪表盘', onClick: () => navigate('/') },
            { key: '/users', icon: <UserOutlined />, label: '用户管理', onClick: () => navigate('/users') },
            {
              key: '/generated-images',
              icon: <PictureOutlined />,
              label: '生成图片',
              onClick: () => navigate('/generated-images'),
            },
            {
              key: '/plaza',
              icon: <FileTextOutlined />,
              label: '广场文章',
              onClick: () => navigate('/plaza'),
            },
            {
              key: '/crawl',
              icon: <CloudDownloadOutlined />,
              label: '离线入库',
              onClick: () => navigate('/crawl'),
            },
          ]}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
          }}
        >
          <LogoutOutlined
            style={{ cursor: 'pointer', fontSize: 18 }}
            title="退出登录"
            onClick={() => {
              clearAuthSession()
              navigate('/login', { replace: true })
            }}
          />
        </Header>
        <Content style={{ margin: 24, padding: 24, background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
