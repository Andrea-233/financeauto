-- ===================================================
-- financeauto 用户订阅/会员系统 SQL 迁移
-- 在 Supabase SQL Editor 中执行此文件
-- ===================================================

-- 1. 创建 subscriptions 表
CREATE TABLE IF NOT EXISTS subscriptions (
  id SERIAL PRIMARY KEY,
  user_id UUID REFERENCES auth.users(id) NOT NULL UNIQUE,
  expiry_date TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
  plan_type TEXT NOT NULL DEFAULT 'trial',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. 启用 RLS
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;

-- 3. 清理旧策略和触发器（如果存在）
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS handle_new_user();
DROP POLICY IF EXISTS "Users_see_own" ON subscriptions;
DROP POLICY IF EXISTS "Users_insert_own" ON subscriptions;
DROP POLICY IF EXISTS "Users_update_own" ON subscriptions;

-- 4. RLS 策略：用户可读写自己的订阅
CREATE POLICY "Users_see_own" ON subscriptions
  FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users_insert_own" ON subscriptions
  FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users_update_own" ON subscriptions
  FOR UPDATE USING (auth.uid() = user_id);

-- 5. RPC：App 注册后调用，创建 7 天试用订阅
CREATE OR REPLACE FUNCTION create_trial_subscription()
RETURNS VOID LANGUAGE sql SECURITY DEFINER
SET search_path = 'public'
AS $$
  INSERT INTO subscriptions (user_id, expiry_date, plan_type)
  VALUES (auth.uid(), NOW() + INTERVAL '7 days', 'trial')
  ON CONFLICT (user_id) DO NOTHING;
$$;

-- 6. 管理员：列出所有订阅（管理员手机：16637316698）
CREATE OR REPLACE FUNCTION admin_list_subscriptions()
RETURNS TABLE(
  user_email TEXT,
  user_nickname TEXT,
  expiry_date TIMESTAMPTZ,
  plan_type TEXT,
  created_at TIMESTAMPTZ
) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  admin_emails TEXT[] := ARRAY['u16637316698@fin.user'];
  caller_email TEXT;
BEGIN
  SELECT email INTO caller_email FROM auth.users WHERE id = auth.uid();
  IF caller_email = ANY(admin_emails) THEN
    RETURN QUERY
    SELECT
      u.email::TEXT,
      u.raw_user_meta_data->>'nickname',
      s.expiry_date,
      s.plan_type,
      s.created_at
    FROM subscriptions s
    JOIN auth.users u ON s.user_id = u.id
    ORDER BY s.expiry_date DESC;
  END IF;
END;
$$;

-- 7. 管理员：设置用户订阅
CREATE OR REPLACE FUNCTION admin_set_subscription(
  target_user_email TEXT,
  new_expiry_date TIMESTAMPTZ,
  new_plan_type TEXT
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  admin_emails TEXT[] := ARRAY['u16637316698@fin.user'];
  caller_email TEXT;
  target_id UUID;
BEGIN
  SELECT email INTO caller_email FROM auth.users WHERE id = auth.uid();
  IF caller_email = ANY(admin_emails) THEN
    SELECT id INTO target_id FROM auth.users WHERE email = target_user_email;
    IF target_id IS NOT NULL THEN
      INSERT INTO subscriptions (user_id, expiry_date, plan_type)
      VALUES (target_id, new_expiry_date, new_plan_type)
      ON CONFLICT (user_id)
      DO UPDATE SET expiry_date = new_expiry_date, plan_type = new_plan_type;
    END IF;
  END IF;
END;
$$;

-- 8. 管理员：删除用户订阅
CREATE OR REPLACE FUNCTION admin_delete_subscription(
  target_user_email TEXT
) RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  admin_emails TEXT[] := ARRAY['u16637316698@fin.user'];
  caller_email TEXT;
  target_id UUID;
BEGIN
  SELECT email INTO caller_email FROM auth.users WHERE id = auth.uid();
  IF caller_email = ANY(admin_emails) THEN
    SELECT id INTO target_id FROM auth.users WHERE email = target_user_email;
    IF target_id IS NOT NULL THEN
      DELETE FROM subscriptions WHERE user_id = target_id;
    END IF;
  END IF;
END;
$$;

-- 9. 给已有老用户补上试用订阅
INSERT INTO subscriptions (user_id, expiry_date, plan_type)
SELECT id, NOW() + INTERVAL '7 days', 'trial'
FROM auth.users u
WHERE NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.user_id = u.id);

-- ===================================================
-- 常用管理命令（在 Supabase SQL Editor 中执行）
-- ===================================================

-- 查看所有用户订阅
-- SELECT * FROM admin_list_subscriptions();

-- 给某用户设置 30 天会员
-- SELECT admin_set_subscription('u166xxxxxxx@fin.user', NOW() + INTERVAL '30 days', 'monthly');

-- 给某用户设置永久会员
-- SELECT admin_set_subscription('u166xxxxxxx@fin.user', '2099-12-31 23:59:59+08', 'permanent');

-- 查看表中所有记录
-- SELECT u.email, u.raw_user_meta_data->>'nickname' AS nickname,
--        s.expiry_date, s.plan_type, s.created_at
-- FROM subscriptions s JOIN auth.users u ON s.user_id = u.id
-- ORDER BY s.expiry_date DESC;
