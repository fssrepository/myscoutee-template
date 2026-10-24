// The native production security chain rejects anonymous requests before MVC
// with 403; the permissive dev chain reaches the controller's 401 check.
export function prepare({ ctx }) {
  return { expectedStatus: ['kvm', 'prod'].includes(ctx.mode) ? 403 : 401 };
}
