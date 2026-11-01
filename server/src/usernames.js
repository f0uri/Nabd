/** Account handles are independent from display names and canonicalized before storage. */
export function normalizeUsername(value) {
  if (typeof value !== 'string') throw new Error('INVALID_USERNAME');
  const username = value.trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z][a-z0-9_]{2,39}$/.test(username)) throw new Error('INVALID_USERNAME');
  return username;
}

export function rethrowUsernameConflict(error) {
  if (error.code === '23505' && error.constraint === 'users_username_lower_key') {
    throw new Error('USERNAME_TAKEN');
  }
  throw error;
}
