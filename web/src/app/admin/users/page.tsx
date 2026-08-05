'use client';

import { useState } from 'react';
import {
  Table,
  Button,
  Modal,
  Toast,
  Popconfirm,
  Tag,
  Input,
  Skeleton,
  Radio,
  RadioGroup,
} from '@douyinfe/semi-ui';
import { useAdminUsers, useRoles, useBanUser, useUnbanUser, useAssignRole } from '@/hooks/useAdmin';
import type { AdminUser } from '@/types/admin';

export default function UsersPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const { data, isLoading } = useAdminUsers({ page, pageSize: 20, search: search || undefined });
  const { data: roles } = useRoles();
  const banMut = useBanUser();
  const unbanMut = useUnbanUser();
  const assignMut = useAssignRole();
  const [assignUser, setAssignUser] = useState<AdminUser | null>(null);
  const [pickedRole, setPickedRole] = useState('');

  const handleAssign = async () => {
    if (!assignUser || !pickedRole) return;
    try {
      await assignMut.mutateAsync({ id: assignUser.id, role: pickedRole });
      Toast.success('角色已分配');
      setAssignUser(null);
    } catch (e: any) {
      Toast.error(e?.message || '分配失败');
    }
  };

  const columns = [
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    { title: '用户名', dataIndex: 'username', key: 'username' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (s: string) => (
        <Tag color={s === 'ACTIVE' ? 'green' : 'red'} size="small">
          {s}
        </Tag>
      ),
    },
    {
      title: '角色',
      dataIndex: 'roles',
      key: 'roles',
      render: (rs: string[]) =>
        rs?.length ? (
          rs.map((r) => (
            <Tag key={r} size="small" className="mr-1">
              {r}
            </Tag>
          ))
        ) : (
          <span className="text-gray-400">-</span>
        ),
    },
    {
      title: '操作',
      key: 'op',
      width: 230,
      render: (_: unknown, record: AdminUser) => (
        <div className="flex gap-2">
          <Button
            size="small"
            type="tertiary"
            onClick={() => {
              setAssignUser(record);
              setPickedRole(record.roles?.[0] ?? '');
            }}
          >
            分配角色
          </Button>
          {record.status === 'ACTIVE' ? (
            <Popconfirm
              title="确定封禁该用户？"
              onConfirm={() =>
                banMut.mutate(record.id, {
                  onSuccess: () => Toast.success('已封禁'),
                  onError: (e: any) => Toast.error(e?.message || '失败'),
                })
              }
            >
              <Button size="small" type="danger">
                封禁
              </Button>
            </Popconfirm>
          ) : (
            <Button
              size="small"
              onClick={() =>
                unbanMut.mutate(record.id, {
                  onSuccess: () => Toast.success('已解封'),
                  onError: (e: any) => Toast.error(e?.message || '失败'),
                })
              }
            >
              解封
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-semibold">用户管理</h1>
        <Input
          value={search}
          onChange={setSearch}
          placeholder="搜索邮箱/用户名，回车搜索"
          onEnterPress={() => setPage(1)}
          showClear
          style={{ width: 260 }}
        />
      </div>
      {isLoading ? (
        <Skeleton placeholder={<Skeleton.Title />} loading active />
      ) : (
        <Table
          columns={columns}
          dataSource={data?.items ?? []}
          rowKey="id"
          pagination={{
            currentPage: page,
            pageSize: 20,
            total: data?.total ?? 0,
            onPageChange: setPage,
          }}
        />
      )}

      <Modal
        title="分配角色"
        visible={assignUser != null}
        onCancel={() => setAssignUser(null)}
        onOk={handleAssign}
        okButtonProps={{ loading: assignMut.isPending }}
      >
        {assignUser && (
          <div>
            <div className="text-sm text-gray-500 mb-3">为 {assignUser.email} 追加一个角色</div>
            <RadioGroup
              value={pickedRole}
              onChange={(e: any) => setPickedRole(e?.target?.value ?? '')}
            >
              {(roles ?? []).map((r) => (
                <Radio key={r.code} value={r.code} className="!block !mb-2">
                  {r.name}（{r.code}）
                </Radio>
              ))}
            </RadioGroup>
          </div>
        )}
      </Modal>
    </div>
  );
}
