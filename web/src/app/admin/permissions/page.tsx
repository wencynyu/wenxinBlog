'use client';

import { useState } from 'react';
import { Table, Button, Modal, Form, Toast, Popconfirm, Skeleton } from '@douyinfe/semi-ui';
import { IconPlus, IconDelete } from '@douyinfe/semi-icons';
import { usePermissions, useCreatePermission, useDeletePermission } from '@/hooks/useAdmin';
import type { Permission, CreatePermissionRequest } from '@/types/permission';

export default function PermissionsPage() {
  const { data: permissions, isLoading } = usePermissions();
  const createMut = useCreatePermission();
  const deleteMut = useDeletePermission();
  const [visible, setVisible] = useState(false);

  const handleDelete = (code: string) => {
    deleteMut.mutate(code, {
      onSuccess: () => Toast.success('已删除'),
      onError: (e: any) => Toast.error(e?.message || '删除失败'),
    });
  };

  const handleSubmit = async (values: CreatePermissionRequest) => {
    try {
      await createMut.mutateAsync(values);
      Toast.success('已创建');
      setVisible(false);
    } catch (e: any) {
      Toast.error(e?.message || '创建失败');
    }
  };

  const columns = [
    { title: 'Code', dataIndex: 'code', key: 'code', width: 200 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: 'Resource', dataIndex: 'resource', key: 'resource', width: 110 },
    { title: 'Action', dataIndex: 'action', key: 'action', width: 100 },
    {
      title: 'Scope',
      dataIndex: 'scope',
      key: 'scope',
      width: 80,
      render: (v: string | null) => v || '-',
    },
    { title: '说明', dataIndex: 'description', key: 'description' },
    {
      title: '操作',
      key: 'op',
      width: 80,
      render: (_: unknown, record: Permission) => (
        <Popconfirm
          title="确定删除该权限？相关角色权限会级联清理。"
          onConfirm={() => handleDelete(record.code)}
        >
          <Button icon={<IconDelete />} type="tertiary" size="small" />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-semibold">权限管理</h1>
        <Button icon={<IconPlus />} theme="solid" onClick={() => setVisible(true)}>
          新建权限
        </Button>
      </div>
      {isLoading ? (
        <Skeleton placeholder={<Skeleton.Title />} loading active />
      ) : (
        <Table
          columns={columns}
          dataSource={permissions ?? []}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      )}

      <Modal title="新建权限" visible={visible} onCancel={() => setVisible(false)} footer={null}>
        <Form onSubmit={handleSubmit} key={visible ? 'open' : 'closed'}>
          <Form.Input
            field="code"
            label="Code（resource:action[:scope]）"
            rules={[{ required: true, message: '必填' }]}
            placeholder="如 post:create"
          />
          <Form.Input field="name" label="名称" rules={[{ required: true, message: '必填' }]} />
          <Form.Input
            field="resource"
            label="Resource"
            rules={[{ required: true, message: '必填' }]}
            placeholder="如 post"
          />
          <Form.Input
            field="action"
            label="Action"
            rules={[{ required: true, message: '必填' }]}
            placeholder="如 create"
          />
          <Form.Input field="scope" label="Scope（可选）" placeholder="own / any" />
          <Form.TextArea field="description" label="说明" />
          <div className="flex justify-end gap-2 mt-4">
            <Button onClick={() => setVisible(false)}>取消</Button>
            <Button theme="solid" htmlType="submit" loading={createMut.isPending}>
              创建
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
