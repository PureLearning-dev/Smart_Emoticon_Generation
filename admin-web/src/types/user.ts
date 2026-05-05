/** 对齐 smart_meter User 实体 JSON（passwordHash 已被 @JsonIgnore 隐藏） */
export interface UserRow {
  id: number
  username?: string
  openid?: string
  nickname?: string
  avatarUrl?: string
  status?: number
  userType?: number
  createTime?: string
  updateTime?: string
}
