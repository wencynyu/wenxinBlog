'use client';

import { useState, useEffect, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  Table,
  Button,
  Modal,
  Form,
  Toast,
  Popconfirm,
  Tag,
  Checkbox,
  CheckboxGroup,
  Skeleton,
  Spin,
} from '@douyinfe/semi-ui';
import { IconPlus, IconDelete, IconKey } from '@douyinfe/semi-icons';
import * as adminApi from '@/lib/api/admin';
import {
  useRoles,
  useRoleDetail,
  usePermissions,
  useCreateRole,
  useDeleteRole,
} from '@/hooks/useAdmin';
import type { Role, Permission, CreateRoleRequest } from '@/types/permission';

export default function RolesPage() {
  const queryClient = useQueryClient();
  const { data: roles, isLoading } = useRoles();
  const { data: allPermissions } = usePermissions();
  const createMut = useCreateRole();
  const deleteMut = useDeleteRole();

  const [createVisible, setCreateVisible] = useState(false);
  const [permRoleId, setPermRoleId] = useState<number | null>(null);
  const { data: roleDetail, isFetching: detailFetching } = useRoleDetail(permRoleId);
  const currentCodes = roleDetail?.permissions.map((p) => p.code) ?? [];

  // 权限同步：grant 新增 + revoke 移除（后端 grant 是追加、revoke 是单移除）
  const handleSync = async (codes: string[]) => {
    if (permRoleId == null) return;
    const toAdd = codes.filter((c) => !currentCodes.includes(c));
    const toRemove = currentCodes.filter((c) => !codes.includes(c));
    try {
      if (toAdd.length) await adminApi.grantRolePermissions(permRoleId, toAdd);
      for (const c of toRemove) await adminApi.revokeRolePermission(permRoleId, c);
      queryClient.invalidateQueries({ queryKey: ['admin', 'role', permRoleId] });
      Toast.success('权限已更新');
      setPermRoleId(null);
    } catch (e: any) {
      Toast.error(e?.message || '更新失败');
    }
  };

  const handleCreate = async (values: CreateRoleRequest) => {
    try {
      await createMut.mutateAsync(values);
      Toast.success('已创建');
      setCreateVisible(false);
    } catch (e: any) {
      Toast.error(e?.message || '创建失败');
    }
  };

  const columns = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 140 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: 'Level', dataIndex: 'level', key: 'level', width: 70 },
    {
      title: '类型',
      dataIndex: 'isSystem',
      key: 'isSystem',
      width: 90,
      render: (v: boolean) =>
        v ? (
          <Tag color="blue" size="small">
            系统
          </Tag>
        ) : (
          <Tag size="small">自定义</Tag>
        ),
    },
    {
      title: '操作',
      key: 'op',
      width: 170,
      render: (_: unknown, record: Role) => (
        <div className="flex gap-2">
          <Button
            icon={<IconKey />}
            type="tertiary"
            size="small"
            onClick={() => setPermRoleId(record.id)}
          >
            权限
          </Button>
          {!record.isSystem && (
            <Popconfirm
              title="确定删除该角色？"
              onConfirm={() =>
                deleteMut.mutate(record.id, {
                  onSuccess: () => Toast.success('已删除'),
                  onError: (e: any) => Toast.error(e?.message || '删除失败'),
                })
              }
            >
              <Button icon={<IconDelete />} type="tertiary" size="small" />
            </Popconfirm>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-semibold">角色管理</h1>
        <Button icon={<IconPlus />} theme="solid" onClick={() => setCreateVisible(true)}>
          新建角色
        </Button>
      </div>
      {isLoading ? (
        <Skeleton placeholder={<Skeleton.Title />} loading active />
      ) : (
        <Table
          columns={columns}
          dataSource={roles ?? []}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      )}

      <Modal
        title="新建角色"
        visible={createVisible}
        onCancel={() => setCreateVisible(false)}
        footer={null}
      >
        <Form onSubmit={handleCreate} key={createVisible ? 'open' : 'closed'}>
          <Form.Input
            field="code"
            label="Code"
            rules={[{ required: true, message: '必填' }]}
            placeholder="如 editor"
          />
          <Form.Input field="name" label="名称" rules={[{ required: true, message: '必填' }]} />
          <Form.Input
            field="parentCode"
            label="父角色 Code（可选，用于继承）"
            placeholder="如 user"
          />
          <Form.TextArea field="description" label="说明" />
          <div className="flex justify-end gap-2 mt-4">
            <Button onClick={() => setCreateVisible(false)}>取消</Button>
            <Button theme="solid" htmlType="submit" loading={createMut.isPending}>
              创建
            </Button>
          </div>
        </Form>
      </Modal>

      <PermissionAssignModal
        visible={permRoleId != null}
        roleName={roles?.find((r) => r.id === permRoleId)?.name ?? ''}
        allPermissions={allPermissions ?? []}
        currentCodes={currentCodes}
        loading={detailFetching}
        onCancel={() => setPermRoleId(null)}
        onSubmit={handleSync}
      />
    </div>
  );
}

function PermissionAssignModal({
  visible,
  roleName,
  allPermissions,
  currentCodes,
  loading,
  onCancel,
  onSubmit,
}: {
  visible: boolean;
  roleName: string;
  allPermissions: Permission[];
  currentCodes: string[];
  loading: boolean;
  onCancel: () => void;
  onSubmit: (codes: string[]) => void;
}) {
  const [selected, setSelected] = useState<string[]>(currentCodes);
  useEffect(() => {
    setSelected(currentCodes);
  }, [currentCodes]);

  const groups = useMemo(() => {
    const m = new Map<string, Permission[]>();
    allPermissions.forEach((p) => {
      const arr = m.get(p.resource) ?? [];
      arr.push(p);
      m.set(p.resource, arr);
    });
    return Array.from(m.entries());
  }, [allPermissions]);

  return (
    <Modal
      title={`分配权限 - ${roleName}`}
      visible={visible}
      onCancel={onCancel}
      onOk={() => onSubmit(selected)}
      okButtonProps={{ loading }}
      width={640}
    >
      {loading && currentCodes.length === 0 ? (
        <div className="text-center py-8">
          <Spin />
        </div>
      ) : (
        <div className="max-h-96 overflow-auto space-y-3">
          {groups.map(([res, perms]) => (
            <div key={res}>
              <div className="font-medium text-sm mb-1 text-gray-700">{res}</div>
              <CheckboxGroup value={selected} onChange={(v) => setSelected(v as string[])}>
                {perms.map((p) => (
                  <Checkbox key={p.code} value={p.code} style={{ width: 260 }}>
                    {p.code}
                  </Checkbox>
                ))}
              </CheckboxGroup>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
