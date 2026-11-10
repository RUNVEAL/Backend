package com.runmap.api.map.domain;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import java.util.Set;

/**
 * 이미 달린 길(엣지)에 벌점을 주는 요청 한 번짜리 가중치. 저장하지 않는다.
 *
 * <p>가중치를 늘리기만 하므로 LM(landmark) 휴리스틱이 그대로 유효하다. PoC: priority ×0.2 → 새 길 비율 86.6% → 94.0%.
 */
public class ExploredPenaltyWeighting implements Weighting {

  // ponytail: PoC 값. 서울 그래프로 새 길 비율 eval 후 조정
  public static final double PENALTY = 5.0;

  private final Weighting delegate;
  private final Set<Integer> explored;

  public ExploredPenaltyWeighting(Weighting delegate, Set<Integer> explored) {
    this.delegate = delegate;
    this.explored = explored;
  }

  @Override
  public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
    double w = delegate.calcEdgeWeight(edge, reverse);
    return explored.contains(edge.getEdge()) ? w * PENALTY : w;
  }

  @Override
  public double calcMinWeightPerDistance() {
    return delegate.calcMinWeightPerDistance();
  }

  @Override
  public long calcEdgeMillis(EdgeIteratorState edge, boolean reverse) {
    return delegate.calcEdgeMillis(edge, reverse);
  }

  @Override
  public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
    return delegate.calcTurnWeight(inEdge, viaNode, outEdge);
  }

  @Override
  public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
    return delegate.calcTurnMillis(inEdge, viaNode, outEdge);
  }

  @Override
  public boolean hasTurnCosts() {
    return delegate.hasTurnCosts();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }
}
