import { View, TextInput, Button, StyleSheet, Alert, TouchableOpacity, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { useState } from 'react';

const API_URL = 'http://localhost:8080';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = async () => {
    try {
      const res = await fetch(`${API_URL}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const data = await res.json();
      if (res.ok) {
        router.replace('/(tabs)');
      } else {
        Alert.alert('登录失败', data.message);
      }
    } catch (err) {
      Alert.alert('网络错误', '请检查网络连接');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>WenxinBlog</Text>
      <TextInput style={styles.input} placeholder="邮箱" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
      <TextInput style={styles.input} placeholder="密码" value={password} onChangeText={setPassword} secureTextEntry />
      <Button title="登录" onPress={handleLogin} />
      <TouchableOpacity onPress={() => router.push('/(auth)/register')}>
        <Text style={styles.link}>没有账号？立即注册</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 32, fontWeight: 'bold', textAlign: 'center', marginBottom: 40 },
  input: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 14, marginBottom: 16, fontSize: 16 },
  link: { textAlign: 'center', color: '#4A90D9', marginTop: 20, fontSize: 14 },
});
