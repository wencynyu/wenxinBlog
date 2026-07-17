import UserProfileView from './UserProfileView';

export const dynamic = 'force-dynamic';

export default function UserProfilePage({ params }: { params: { id: string } }) {
  return <UserProfileView userId={params.id} />;
}
