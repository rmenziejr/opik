import { QueryFunctionContext, useQuery } from "@tanstack/react-query";
import axios from "axios";
import api, { QueryConfig } from "./api";
import { Workspace } from "./types";
import { DEFAULT_WORKSPACE_NAME } from "@/constants/user";

// Default workspace for basic auth mode (self-hosted)
const DEFAULT_WORKSPACE: Workspace = {
  createdAt: Date.now(),
  workspaceId: "default",
  workspaceName: DEFAULT_WORKSPACE_NAME,
  workspaceOwner: "admin",
  workspaceCreator: "admin",
  organizationId: "default",
  collaborationFeaturesDisabled: true,
  default: true,
};

const getUserInvitedWorkspaces = async ({
  signal,
}: QueryFunctionContext): Promise<Workspace[]> => {
  try {
    const response = await api.get<Workspace[]>(`/workspaces`, {
      signal,
      params: { withoutExtendedData: true },
    });
    return response.data;
  } catch (error) {
    // If comet endpoint fails, return default workspace for basic auth mode
    if (axios.isAxiosError(error)) {
      return [DEFAULT_WORKSPACE];
    }
    throw error;
  }
};

// workspaces to which a user has been invited
export default function useUserInvitedWorkspaces(
  options?: QueryConfig<Workspace[]>,
) {
  return useQuery({
    // enabled: true is needed just to overcome the eslint requirement to have parameters
    queryKey: ["user-invited-workspaces", { enabled: true }],
    queryFn: (context) => getUserInvitedWorkspaces(context),
    ...options,
    enabled: Boolean(options?.enabled),
  });
}
