import { api } from "@/services/api";

export interface UserItem {
  id: string;
  username: string;
  role: string;
  avatar?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface UserCreatePayload {
  username: string;
  password: string;
  role?: string;
  avatar?: string | null;
}

export interface UserUpdatePayload {
  username?: string;
  password?: string;
  role?: string;
  avatar?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export async function getUsersPage(
  current = 1,
  size = 10,
  keyword?: string
): Promise<PageResult<UserItem>> {
  return api.get<PageResult<UserItem>, PageResult<UserItem>>("/users", {
    params: { current, size, keyword: keyword || undefined }
  });
}

export async function createUser(payload: UserCreatePayload): Promise<string> {
  return api.post<string, string>("/users", payload);
}

export async function updateUser(id: string, payload: UserUpdatePayload): Promise<void> {
  await api.put(`/users/${id}`, payload);
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/users/${id}`);
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await api.put("/user/password", payload);
}

async function postAvatarFile(url: string, file: File): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);
  return api.post<string, string>(url, formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
}

/** 当前用户上传并更新头像，返回公开 URL */
export async function uploadMyAvatar(file: File): Promise<string> {
  return postAvatarFile("/user/avatar", file);
}

/** 管理员上传头像文件（不落库），返回公开 URL，供创建用户表单回填 */
export async function uploadAvatarAsset(file: File): Promise<string> {
  return postAvatarFile("/users/avatar/upload", file);
}

/** 管理员为指定用户上传并更新头像，返回公开 URL */
export async function uploadUserAvatar(userId: string, file: File): Promise<string> {
  return postAvatarFile(`/users/${userId}/avatar`, file);
}
