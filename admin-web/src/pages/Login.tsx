import { Button, Card, Form, Input, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { checkCredentials, setAuthSession } from '@/config/auth'

export default function LoginPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%)',
      }}
    >
      <Card style={{ width: 400, boxShadow: '0 8px 24px rgba(0,0,0,0.08)' }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          智能表情包 · 管理后台
        </Typography.Title>
        <Form
          form={form}
          layout="vertical"
          onFinish={(v: { username: string; password: string }) => {
            if (checkCredentials(v.username.trim(), v.password)) {
              setAuthSession()
              message.success('登录成功')
              navigate('/', { replace: true })
            } else {
              message.error('用户名或密码错误')
            }
          }}
        >
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large">
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
